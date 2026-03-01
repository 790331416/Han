import { get, post } from '@/utils/request'
import type { PageResult, PageQuery } from '@/types'

export interface Post {
  id: number
  postCode: string
  postName: string
  postSort: number
  status: number
  remark?: string
  createTime?: string
}

export interface PostQuery extends PageQuery {
  postCode?: string
  postName?: string
  status?: number
}

export interface PostForm {
  id?: number
  postCode: string
  postName: string
  postSort?: number
  status?: number
  remark?: string
}

export function listPost(query: PostQuery) {
  return get<PageResult<Post>>('/system/post/list', query)
}

export function listAllPosts() {
  return get<Post[]>('/system/post/all')
}

export function getPost(id: number) {
  return get<Post>(`/system/post/${id}`)
}

export function addPost(data: PostForm) {
  return post<void>('/system/post', data)
}

export function updatePost(data: PostForm) {
  return post<void>('/system/post/edit', data)
}

export function deletePost(id: number) {
  return post<void>(`/system/post/remove/${id}`)
}

export function deletePosts(ids: number[]) {
  return post<void>('/system/post/remove', ids)
}
