import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import LoginView from '@/views/LoginView.vue'

const mocks = vi.hoisted(() => ({ methods: vi.fn(), send: vi.fn(), email: vi.fn(), github: vi.fn() }))
vi.mock('@/api/auth', async (original) => ({
  ...(await original<typeof import('@/api/auth')>()),
  getLoginMethods: mocks.methods, sendEmailCode: mocks.send, loginWithEmail: mocks.email, authorizeGithub: mocks.github,
}))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn() } }))

async function render() {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/login', component: LoginView }, { path: '/', component: { template: '<div>首页</div>' } },
  ] })
  await router.push('/login')
  const wrapper = mount(LoginView, { global: { plugins: [createPinia(), router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('Alternative login', () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.methods.mockResolvedValue({ email: true, github: false }) })
  afterEach(() => vi.useRealTimers())

  it('shows unavailable configuration and disables provider actions', async () => {
    mocks.methods.mockResolvedValue({ email: false, github: false })
    const { wrapper } = await render()
    await wrapper.findAll('.login-tabs button')[1]!.trigger('click')
    expect(wrapper.text()).toContain('邮箱登录尚未配置')
    expect(wrapper.find('.code-button').attributes('disabled')).toBeDefined()
    expect(wrapper.find('.github-button').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('shows the GitHub icon and requests authorization when enabled', async () => {
    mocks.methods.mockResolvedValue({ email: false, github: true })
    mocks.github.mockRejectedValue({ response: { data: { message: '暂时无法连接 GitHub' } } })
    const { wrapper } = await render()
    expect(wrapper.find('.github-icon').element.tagName.toLowerCase()).toBe('svg')
    expect(wrapper.find('.github-button').text()).toBe('使用 GitHub 登录')
    expect(wrapper.text()).not.toContain('微信')
    await wrapper.find('.github-button').trigger('click')
    await flushPromises()
    expect(mocks.github).toHaveBeenCalledOnce()
    expect(wrapper.find('[role="alert"]').text()).toBe('暂时无法连接 GitHub')
    expect(wrapper.find('.github-button').attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('sends a code and prevents repeat requests during countdown', async () => {
    const { wrapper } = await render()
    mocks.send.mockResolvedValue({ message: '请查收验证码' })
    await wrapper.findAll('.login-tabs button')[1]!.trigger('click')
    await wrapper.find('#login-email').setValue('user@example.com')
    await wrapper.find('.code-button').trigger('click')
    await flushPromises()
    expect(mocks.send).toHaveBeenCalledWith('user@example.com')
    expect(wrapper.find('.code-button').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('60 秒后重发')
    wrapper.unmount()
  })

  it('logs in with the email code and navigates to the home page', async () => {
    const { wrapper, router } = await render()
    mocks.email.mockResolvedValue({ id: 1, username: 'user', admin: false })
    await wrapper.findAll('.login-tabs button')[1]!.trigger('click')
    await wrapper.find('#login-email').setValue('user@example.com')
    await wrapper.find('#login-code').setValue('123456')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(mocks.email).toHaveBeenCalledWith('user@example.com', '123456')
    expect(router.currentRoute.value.path).toBe('/')
    wrapper.unmount()
  })

  it('keeps the user on the page and displays expired code errors', async () => {
    const { wrapper } = await render()
    mocks.email.mockRejectedValue({ response: { status: 401 } })
    await wrapper.findAll('.login-tabs button')[1]!.trigger('click')
    await wrapper.find('#login-email').setValue('user@example.com')
    await wrapper.find('#login-code').setValue('123456')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toContain('验证码错误、已过期')
    wrapper.unmount()
  })
})
