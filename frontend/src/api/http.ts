import axios from 'axios'
import { message } from 'ant-design-vue'
import type { ApiResponse } from '../types/api'

const http = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// Response interceptor
http.interceptors.response.use(
  (response) => {
    const resp = response.data as ApiResponse<any>
    if (resp.code !== 200) {
      message.error(resp.message || '请求失败')
      return Promise.reject(new Error(resp.message || '请求失败'))
    }
    return response
  },
  (error) => {
    if (error.response) {
      message.error(error.response.data?.message || '网络请求失败')
    } else if (error.request) {
      message.error('网络请求失败，请检查服务是否启动')
    } else {
      message.error('请求配置错误')
    }
    return Promise.reject(error)
  }
)

export default http
