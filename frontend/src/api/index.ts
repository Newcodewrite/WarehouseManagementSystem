import axios, { type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'

/** 后端统一响应结构 */
export interface ApiResponse<T = unknown> {
  code: number
  msg: string
  data: T
}

/** 分页响应结构 */
export interface Paginated<T> {
  records: T[]
  total: number
  size?: number
  current?: number
}

export const TOKEN_KEY = 'wms_token'
export const USER_KEY = 'wms_user'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截器：附加 token
request.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：统一处理 code 与 401（运行时已解包为业务 data，类型上按 AxiosResponse 签名适配）
request.interceptors.response.use(
  (response): AxiosResponse | Promise<AxiosResponse> => {
    const res = response.data as ApiResponse
    if (res.code !== 200) {
      ElMessage.error(res.msg || '请求失败')
      return Promise.reject(new Error(res.msg || '请求失败'))
    }
    return res.data as unknown as AxiosResponse
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      ElMessage.error('登录已过期，请重新登录')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    } else {
      const msg = error.response?.data?.msg || error.message || '网络异常，请稍后重试'
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

/** GET 请求（返回拦截器处理后的 data） */
export function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return request.get(url, { params }) as Promise<T>
}

/** POST 请求 */
export function post<T>(url: string, data?: unknown): Promise<T> {
  return request.post(url, data) as Promise<T>
}

/** POST 请求（自定义超时，用于 AI 长对话/工具调用） */
export function postT<T>(url: string, data?: unknown, timeout = 30000): Promise<T> {
  return request.post(url, data, { timeout }) as Promise<T>
}

/** 文件上传（multipart/form-data） */
export function upload<T>(url: string, formData: FormData, timeout = 120000): Promise<T> {
  return request.post(url, formData, { timeout }) as Promise<T>
}

/** PUT 请求（支持 query 参数） */
export function put<T>(url: string, data?: unknown, params?: Record<string, unknown>): Promise<T> {
  return request.put(url, data, { params }) as Promise<T>
}

/** DELETE 请求 */
export function del<T>(url: string): Promise<T> {
  return request.delete(url) as Promise<T>
}

export default request
