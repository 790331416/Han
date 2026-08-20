#!/usr/bin/env node
import { readFileSync } from 'node:fs'
import { gunzipSync } from 'node:zlib'

const args = {}
for (let index = 2; index < process.argv.length; index += 1) {
  const key = process.argv[index]
  if (key.startsWith('--')) args[key.slice(2)] = process.argv[index + 1]
}
const input = args.input
const tenantId = args['tenant-id']
const idBase = args['id-base']
if (!input || !/^\d+$/.test(tenantId || '') || !/^\d+$/.test(idBase || '')) {
  throw new Error('用法：node scripts/generate-national-region-seed.mjs --input <area_info.sql|area_code_2024.csv.gz> --tenant-id <租户ID> --id-base <全局唯一ID基数> [--check]')
}

const byCode = new Map()
const source = readText(input)
if (input.endsWith('.csv.gz') || input.endsWith('.csv')) {
  loadNationalCsv(source, byCode)
} else {
  loadAreaInfoSql(source, byCode)
}

const base = BigInt(idBase)
const rows = [...byCode.values()].sort((left, right) => Number(left.code) - Number(right.code))
// 先完整校验，再输出 SQL。否则重定向后的半份 SQL 容易被误当成可执行初始化文件。
for (const row of rows) {
  const parentCode = parentCodeOf(row)
  if (parentCode && !byCode.has(parentCode)) throw new Error(`区域 ${row.code} 缺少父区域 ${parentCode}`)
}
verifyNationalBaseline(input, rows)
if (Object.hasOwn(args, 'check')) {
  console.log(JSON.stringify({ total: rows.length, levels: countByLevel(rows), input }, null, 2))
  process.exit(0)
}
console.log('SET NAMES utf8mb4;')
console.log(`-- 全国行政区划四级基线：${input.endsWith('.csv.gz') ? '2024（源数据说明基于国家统计局 2023 年统计用区划）' : 'area_info.sql'}；仅含省、市、区县、乡镇/街道。`)
console.log('START TRANSACTION;')
for (const row of rows) {
  const parentCode = parentCodeOf(row)
  const parentId = parentCode ? base + BigInt(parentCode) : null
  const ancestry = ancestorsOf(row, byCode)
  const ancestors = `0${ancestry.map(code => `,${base + BigInt(code)}`).join('')}`
  const regionLevel = ['PROVINCE', 'CITY', 'DISTRICT', 'STREET'][row.level - 1]
  console.log(`INSERT IGNORE INTO edu_region (id, tenant_id, parent_id, ancestors, node_level, region_code, region_name, region_level, source_system, sort, status, del_flag) VALUES (${base + BigInt(row.code)}, ${tenantId}, ${parentId ?? 'NULL'}, '${ancestors}', ${ancestry.length}, '${row.code}', '${escapeSql(row.name)}', '${regionLevel}', 'NATIONAL', 0, 0, 0);`)
}
console.log('COMMIT;')

function readText(path) {
  const bytes = readFileSync(path)
  return path.endsWith('.gz') ? gunzipSync(bytes).toString('utf8') : bytes.toString('utf8')
}

function loadAreaInfoSql(source, target) {
  const columns = ['pkId', 'provinceCode', 'provinceName', 'cityCode', 'cityName', 'countyCode', 'countyName', 'streetCode', 'streetName', 'level']
  for (const match of source.matchAll(/^INSERT INTO `area_info` VALUES \((.+)\);$/gm)) {
    const values = splitValues(match[1])
    const row = Object.fromEntries(columns.map((name, index) => [name, values[index]]))
    const level = Number(row.level)
    const code = level === 1 ? row.provinceCode : level === 2 ? row.cityCode : level === 3 ? row.countyCode : row.streetCode
    const name = level === 1 ? row.provinceName : level === 2 ? row.cityName : level === 3 ? row.countyName : row.streetName
    if (!name || !/^(?:\d{2}|\d{4}|\d{6}|\d{9})$/.test(code || '') || ![1, 2, 3, 4].includes(level)) continue
    target.set(code, { code, name, level })
  }
}

function loadNationalCsv(source, target) {
  const records = []
  const fullCodeToCode = new Map()
  for (const line of source.split(/\r?\n/)) {
    if (!line) continue
    const [fullCode, name, rawLevel, parentFullCode] = line.split(',', 4)
    const level = Number(rawLevel)
    if (!name || !/^[1-4]$/.test(rawLevel || '') || !/^\d{12}$/.test(fullCode || '')) continue
    const code = fullCode.slice(0, [2, 4, 6, 9][level - 1])
    records.push({ code, name, level, fullCode, parentFullCode })
    fullCodeToCode.set(fullCode, code)
  }
  for (const record of records) {
    const parentCode = record.parentFullCode === '0' ? null : fullCodeToCode.get(record.parentFullCode)
    target.set(record.code, { code: record.code, name: record.name, level: record.level, parentCode })
  }
}

function countByLevel(rows) {
  return Object.fromEntries([1, 2, 3, 4].map(level => [level, rows.filter(row => row.level === level).length]))
}

function verifyNationalBaseline(input, rows) {
  if (!input.endsWith('area_code_2024.csv.gz')) return
  const actual = countByLevel(rows)
  const expected = { 1: 31, 2: 343, 3: 3255, 4: 41351 }
  for (const level of [1, 2, 3, 4]) {
    if (actual[level] !== expected[level]) throw new Error(`2024 全国区域基线第 ${level} 级数量不正确：期望 ${expected[level]}，实际 ${actual[level]}`)
  }
}

function parentCodeOf(row) {
  if (row.parentCode !== undefined) return row.parentCode
  return row.level === 1 ? null : row.code.slice(0, row.level === 2 ? 2 : row.level === 3 ? 4 : 6)
}

function ancestorsOf(row, byCode) {
  const reverse = []
  const seen = new Set([row.code])
  let parentCode = parentCodeOf(row)
  while (parentCode) {
    if (!seen.add(parentCode)) throw new Error(`区域 ${row.code} 存在父级循环`)
    reverse.push(parentCode)
    parentCode = parentCodeOf(byCode.get(parentCode))
  }
  return reverse.reverse()
}

function splitValues(source) {
  const values = []
  let value = ''
  let quoted = false
  for (let index = 0; index < source.length; index++) {
    const char = source[index]
    if (char === "'" && source[index - 1] !== '\\') quoted = !quoted
    if (char === ',' && !quoted) { values.push(readValue(value)); value = ''; continue }
    value += char
  }
  values.push(readValue(value))
  return values
}

function readValue(value) {
  const trimmed = value.trim()
  return trimmed === 'NULL' ? null : trimmed.slice(1, -1).replace(/\\'/g, "'").replace(/\\\\/g, '\\')
}

function escapeSql(value) { return value.replace(/'/g, "''") }
