import type { EducationOrganizationNode } from '@/api/education'

export interface SchoolTreeOption {
  value: string | number
  label: string
  disabled: boolean
  children: SchoolTreeOption[]
}

export interface SchoolOption { id: string | number; schoolName: string; schoolCode: string; schoolManageType?: string }

/** 只有校区和独立学校承载学年、班级、设备、教室等业务数据。 */
export function isOperationalSchool(node: EducationOrganizationNode) {
  return node.orgType === 'SCHOOL' && ['CAMPUS', 'INDEPENDENT'].includes(node.schoolManageType || '')
}

/** 教育局、中心校只展示层级；校区和独立学校可作为业务归属。 */
export function toSchoolTree(nodes: EducationOrganizationNode[]): SchoolTreeOption[] {
  return nodes.map(node => ({
    value: node.id,
    label: node.schoolName,
    disabled: !isOperationalSchool(node),
    children: toSchoolTree(node.children || [])
  }))
}

export function schoolOptions(nodes: EducationOrganizationNode[]): SchoolOption[] {
  const result: SchoolOption[] = []
  const stack = [...nodes]
  while (stack.length) {
    const node = stack.pop()!
    if (isOperationalSchool(node)) result.push({ id: node.id, schoolName: node.schoolName, schoolCode: node.schoolCode, schoolManageType: node.schoolManageType })
    stack.push(...(node.children || []))
  }
  return result
}
