/**
 * 树结构公共工具。
 *
 * 背景：菜单、部门、角色授权、租户套餐几个页面各自就地实现了一份树装配与叶子过滤，
 * 其中 `buildTree` 没有成环防护——数据一旦出现 `A.parentId=B, B.parentId=A` 或自引用，
 * 相关节点既不会成为根、也不会被任何根引用，会静默从树里消失，前端不报错、用户看不到丢了节点。
 * 这里统一收口，并把「挂不上父节点的数据一律提升为根 + 控制台告警」作为兜底。
 */

/** 树节点的最小约束：有自身 id、父 id，以及可选的 children。 */
export interface TreeNodeLike<T> {
  id?: string | number
  parentId?: string | number | null
  children?: T[]
}

export interface BuildTreeOptions {
  /** 根节点的 parentId 取值，默认把 0 / '0' / null / undefined 都当根。 */
  rootParentId?: string | number | null
  /** 深度上限，超过后剩余节点提升为根，避免异常数据把渲染撑爆。 */
  maxDepth?: number
}

const DEFAULT_MAX_DEPTH = 32

/**
 * id 统一转成字符串再比较。
 *
 * 后端 Long 型主键经 Jackson 序列化后可能是数字也可能是字符串，
 * 直接用 Map 严格键匹配会让整棵树退化成平铺列表。
 */
function toKey(value: string | number | null | undefined): string {
  return value === null || value === undefined ? '' : String(value)
}

function isRootParent(parentId: string | number | null | undefined, rootParentId?: string | number | null): boolean {
  const key = toKey(parentId)
  if (rootParentId !== undefined) {
    return key === toKey(rootParentId)
  }
  return key === '' || key === '0'
}

/**
 * 由平铺列表装配树。
 *
 * 保证：任何一条输入数据都会出现在结果里——挂得上父节点就挂上，挂不上（父节点不存在、
 * 成环、超过深度上限）就提升为根并打印告警，绝不静默丢节点。
 */
export function buildTree<T extends TreeNodeLike<T>>(list: T[], options: BuildTreeOptions = {}): T[] {
  const { rootParentId, maxDepth = DEFAULT_MAX_DEPTH } = options
  const nodeMap = new Map<string, T>()
  const roots: T[] = []

  for (const item of list) {
    nodeMap.set(toKey(item.id), { ...item, children: [] })
  }

  for (const item of list) {
    const node = nodeMap.get(toKey(item.id))
    if (!node) continue

    if (isRootParent(item.parentId, rootParentId)) {
      roots.push(node)
      continue
    }

    const parent = nodeMap.get(toKey(item.parentId))
    if (!parent || parent === node || isAncestorOf(node, parent, nodeMap, rootParentId, maxDepth)) {
      console.warn(`[buildTree] 节点 ${toKey(item.id)} 无法挂到父节点 ${toKey(item.parentId)}，已提升为根节点`)
      roots.push(node)
      continue
    }

    parent.children = parent.children || []
    parent.children.push(node)
  }

  return roots
}

/**
 * 判断 `candidate` 是否是 `node` 的后代——是的话把 node 挂上去就会成环。
 */
function isAncestorOf<T extends TreeNodeLike<T>>(
  node: T,
  candidate: T,
  nodeMap: Map<string, T>,
  rootParentId: string | number | null | undefined,
  maxDepth: number
): boolean {
  const nodeKey = toKey(node.id)
  const visited = new Set<string>()
  let cursor: T | undefined = candidate
  let depth = 0

  while (cursor && depth < maxDepth) {
    const cursorKey = toKey(cursor.id)
    if (cursorKey === nodeKey) {
      return true
    }
    if (visited.has(cursorKey)) {
      return true
    }
    visited.add(cursorKey)

    if (isRootParent(cursor.parentId, rootParentId)) {
      return false
    }
    cursor = nodeMap.get(toKey(cursor.parentId))
    depth += 1
  }

  // 走到深度上限还没到根，按成环处理，宁可提升为根也不要爆栈。
  return depth >= maxDepth
}

/**
 * 收集一个节点的全部祖先 id。
 *
 * 带 visited 集合与深度上限，异常数据不会栈溢出（原实现是无防护递归）。
 */
export function collectParentIds<T extends TreeNodeLike<T>>(
  list: T[],
  targetId: string | number,
  maxDepth = DEFAULT_MAX_DEPTH
): Array<string | number> {
  const byId = new Map<string, T>()
  for (const item of list) {
    byId.set(toKey(item.id), item)
  }

  const parents: Array<string | number> = []
  const visited = new Set<string>()
  let cursor = byId.get(toKey(targetId))
  let depth = 0

  while (cursor && depth < maxDepth) {
    const parentKey = toKey(cursor.parentId)
    if (!parentKey || parentKey === '0' || visited.has(parentKey)) {
      break
    }
    visited.add(parentKey)

    const parent = byId.get(parentKey)
    if (!parent) {
      break
    }
    parents.push(parent.id as string | number)
    cursor = parent
    depth += 1
  }

  return parents
}

/**
 * 从选中集合里剔除「因为子节点被选中而半选」的父节点，只保留真正的叶子。
 *
 * 树形权限控件回填时用得到：父节点混在提交值里会让后端把整棵子树当成全选。
 */
export function filterLeafIds<T extends TreeNodeLike<T>>(
  list: T[],
  checkedIds: Array<string | number>
): Array<string | number> {
  const parentKeys = new Set<string>()
  for (const item of list) {
    const parentKey = toKey(item.parentId)
    if (parentKey && parentKey !== '0') {
      parentKeys.add(parentKey)
    }
  }

  return checkedIds.filter((id) => !parentKeys.has(toKey(id)))
}
