<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import PageContainer from '@/components/layout/PageContainer.vue'
import {
  navLabels,
  trainingTimeStatusLabels,
  trainingTypeLabels,
  postTypeLabels,
} from '@/constants/labels'
import { listPlans } from '@/api/training'
import { listPosts } from '@/api/discussion'
import type { PlanSummary } from '@/types/training'
import type { PostSummary, PostType } from '@/types/discussion'

const auth = useAuthStore()
const router = useRouter()
const planTab = ref<'MY_CREATED' | 'MY_JOINED'>('MY_CREATED')
const postCategory = ref<PostType | ''>('')
const postCategories: { value: PostType | ''; label: string }[] = [
  { value: '', label: '全部' },
  ...Object.entries(postTypeLabels).map(([value, label]) => ({ value: value as PostType, label })),
]
const plans = ref<PlanSummary[]>([])
const posts = ref<PostSummary[]>([])
const plansLoading = ref(true)
const postsLoading = ref(true)
const plansError = ref('')
const postsError = ref('')
const planTotal = ref(0)
let plansRequest = 0
let postsRequest = 0

function errorMessage(error: unknown, fallback: string) {
  const err = error as { response?: { status?: number; data?: { message?: string } } }
  if (err.response?.status === 401) {
    void router.replace({ name: 'login', query: { redirect: '/' } })
    return '登录已过期，请重新登录'
  }
  return err.response?.data?.message || fallback
}

async function fetchPlans() {
  const request = ++plansRequest
  plansLoading.value = true
  plansError.value = ''
  try {
    const result = await listPlans({ type: planTab.value, page: 1, size: 5 })
    if (request !== plansRequest) return
    plans.value = result.plans
    planTotal.value = result.total
  } catch (error) {
    if (request === plansRequest)
      plansError.value = errorMessage(error, '训练计划加载失败，请稍后重试')
  } finally {
    if (request === plansRequest) plansLoading.value = false
  }
}

async function fetchPosts() {
  const request = ++postsRequest
  postsLoading.value = true
  postsError.value = ''
  try {
    const result = await listPosts({
      page: 1,
      size: 5,
      ...(postCategory.value ? { postType: postCategory.value } : {}),
    })
    if (request === postsRequest) posts.value = result.posts
  } catch (error) {
    if (request === postsRequest) postsError.value = errorMessage(error, '讨论加载失败，请稍后重试')
  } finally {
    if (request === postsRequest) postsLoading.value = false
  }
}

function formatDate(value: string) {
  return new Date(value).toLocaleDateString('zh-CN')
}

watch(planTab, fetchPlans)
watch(postCategory, fetchPosts)
onMounted(() => {
  void fetchPlans()
  void fetchPosts()
})
onUnmounted(() => {
  plansRequest++
  postsRequest++
})
</script>

<template>
  <PageContainer>
    <template #header>
      <div class="welcome-section">
        <h1 class="welcome-title">欢迎回来，{{ auth.user?.nickname || auth.user?.username }}</h1>
        <p v-if="auth.isAdmin" class="role-badge">管理员</p>
      </div>
    </template>

    <div class="home-grid">
      <section class="home-card">
        <h2 class="card-title">快速入口</h2>
        <div class="quick-links">
          <RouterLink to="/problems" class="quick-link">
            <span class="link-icon">&#128218;</span>
            <span class="link-label">{{ navLabels.problems }}</span>
          </RouterLink>
          <RouterLink to="/problems/create" class="quick-link">
            <span class="link-icon">&#10133;</span>
            <span class="link-label">创建题目</span>
          </RouterLink>
          <RouterLink to="/my/problems" class="quick-link">
            <span class="link-icon">&#128451;</span>
            <span class="link-label">{{ navLabels.myProblems }}</span>
          </RouterLink>
          <RouterLink v-if="auth.isAdmin" to="/admin/problems" class="quick-link">
            <span class="link-icon">&#128202;</span>
            <span class="link-label">{{ navLabels.adminProblems }}</span>
          </RouterLink>
          <RouterLink to="/training-plans" class="quick-link">
            <span class="link-icon">&#128220;</span>
            <span class="link-label">{{ navLabels.trainingPlans }}</span>
          </RouterLink>
          <RouterLink to="/posts" class="quick-link">
            <span class="link-icon">&#128172;</span>
            <span class="link-label">{{ navLabels.discussions }}</span>
          </RouterLink>
          <RouterLink to="/leaderboard" class="quick-link">
            <span class="link-icon">&#127942;</span>
            <span class="link-label">{{ navLabels.leaderboard }}</span>
          </RouterLink>
        </div>
      </section>

      <section class="home-card" aria-labelledby="my-plans-title" :aria-busy="plansLoading">
        <div class="section-heading">
          <h2 id="my-plans-title" class="section-title">我的训练计划</h2>
          <div class="section-actions">
            <RouterLink
              :to="{ name: 'training-plans', query: { type: planTab } }"
              class="section-link"
              >查看全部</RouterLink
            >
            <RouterLink to="/training-plans/create" class="section-action">创建计划</RouterLink>
          </div>
        </div>
        <div class="plan-filters" aria-label="我的训练计划筛选">
          <button
            type="button"
            :class="{ selected: planTab === 'MY_CREATED' }"
            :aria-pressed="planTab === 'MY_CREATED'"
            @click="planTab = 'MY_CREATED'"
          >
            我创建的
          </button>
          <button
            type="button"
            :class="{ selected: planTab === 'MY_JOINED' }"
            :aria-pressed="planTab === 'MY_JOINED'"
            @click="planTab = 'MY_JOINED'"
          >
            我加入的
          </button>
          <span v-if="!plansLoading && !plansError" class="plan-total"
            >共 {{ planTotal }} 个计划</span
          >
        </div>
        <p v-if="plansLoading" class="section-status" role="status">正在加载训练计划…</p>
        <div v-else-if="plansError" class="section-status section-error" role="alert">
          <p>{{ plansError }}</p>
          <button type="button" class="retry-button" @click="fetchPlans">重试</button>
        </div>
        <div v-else-if="!plans.length" class="section-empty">
          <p>{{ planTab === 'MY_CREATED' ? '你还没有创建训练计划' : '你还没有加入训练计划' }}</p>
          <span>{{
            planTab === 'MY_CREATED'
              ? '制定自己的刷题目标，开始下一次进步。'
              : '加入公开计划，和社团伙伴一起训练。'
          }}</span>
          <RouterLink
            :to="planTab === 'MY_CREATED' ? '/training-plans/create' : '/training-plans'"
            class="section-link"
          >
            {{ planTab === 'MY_CREATED' ? '创建第一个计划' : '浏览公开计划' }}
          </RouterLink>
        </div>
        <ul v-else class="preview-list plan-preview">
          <li v-for="plan in plans" :key="plan.id">
            <RouterLink
              :to="{ name: 'plan-detail', params: { id: plan.id } }"
              class="preview-row"
              :class="{ 'row-inactive': !plan.active }"
            >
              <div class="preview-main">
                <div class="preview-title-row">
                  <span class="preview-title">{{ plan.title }}</span>
                  <span class="item-badge">{{ trainingTypeLabels[plan.planType] }}</span>
                </div>
                <div class="preview-meta">
                  <span>{{ plan.problemCount }} 道题</span>
                  <span>{{ plan.memberCount }} 位成员</span>
                  <span>{{
                    plan.endTime ? `截止 ${formatDate(plan.endTime)}` : '不限截止时间'
                  }}</span>
                </div>
              </div>
              <span
                class="plan-status"
                :class="plan.active ? plan.timeStatus.toLowerCase() : 'inactive'"
              >
                {{ plan.active ? trainingTimeStatusLabels[plan.timeStatus] : '已停用' }}
              </span>
            </RouterLink>
          </li>
        </ul>
      </section>

      <section class="home-card" aria-labelledby="discussion-title" :aria-busy="postsLoading">
        <div class="section-heading">
          <div class="discussion-heading-main">
            <h2 id="discussion-title" class="section-title">讨论区</h2>
            <div class="discussion-filters" aria-label="讨论分类">
              <button
                v-for="category in postCategories"
                :key="category.value"
                type="button"
                :class="{ selected: postCategory === category.value }"
                :aria-pressed="postCategory === category.value"
                @click="postCategory = category.value"
              >
                {{ category.label }}
              </button>
            </div>
          </div>
          <div class="section-actions">
            <RouterLink
              :to="{ path: '/posts', query: postCategory ? { postType: postCategory } : {} }"
              class="section-link"
              >查看全部</RouterLink
            >
            <RouterLink to="/posts/create" class="section-action">发布讨论</RouterLink>
          </div>
        </div>
        <p v-if="postsLoading" class="section-status" role="status">正在加载最新讨论…</p>
        <div v-else-if="postsError" class="section-status section-error" role="alert">
          <p>{{ postsError }}</p>
          <button type="button" class="retry-button" @click="fetchPosts">重试</button>
        </div>
        <div v-else-if="!posts.length" class="section-empty">
          <p>{{ postCategory ? '当前分类还没有讨论' : '讨论区还没有帖子' }}</p>
          <span>分享一道题的解法，或提出你的疑问。</span>
          <RouterLink to="/posts/create" class="section-link">发布第一篇讨论</RouterLink>
        </div>
        <ul v-else class="preview-list post-preview">
          <li v-for="post in posts" :key="post.id">
            <RouterLink :to="{ name: 'post-detail', params: { id: post.id } }" class="preview-row">
              <div class="preview-main">
                <div class="preview-title-row">
                  <span v-if="post.pinned" class="item-badge pinned-badge">置顶</span>
                  <span class="item-badge">{{
                    postTypeLabels[post.postType] || post.postType
                  }}</span>
                  <span v-if="!post.active" class="item-badge">已停用</span>
                  <span class="preview-title">{{ post.title }}</span>
                </div>
                <div class="preview-meta">
                  <span>{{ post.authorNickname || post.authorUsername }}</span>
                  <span>{{ formatDate(post.createTime) }}</span>
                  <span v-if="post.problemTitle" class="related-problem">{{
                    post.problemTitle
                  }}</span>
                </div>
              </div>
              <div class="post-counts">
                <span>{{ post.commentCount }} 评论</span>
                <span>{{ post.likeCount }} 赞</span>
              </div>
            </RouterLink>
          </li>
        </ul>
      </section>
    </div>
  </PageContainer>
</template>

<style scoped>
.welcome-section {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.welcome-title {
  font-family: var(--font-headline);
  font-size: var(--text-display-lg);
  font-weight: 700;
  line-height: var(--leading-display-lg);
  color: var(--color-on-surface);
}

.role-badge {
  font-size: var(--text-label-sm);
  font-weight: 600;
  color: var(--color-on-primary);
  background: var(--color-primary-container);
  padding: 2px 12px;
  border-radius: 999px;
}

.home-grid {
  display: flex;
  flex-direction: column;
  gap: var(--space-stack-lg);
}

.home-card {
  background: var(--color-surface-card);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 24px;
}

.card-title {
  font-family: var(--font-headline);
  font-size: var(--text-headline-sm);
  font-weight: 600;
  color: var(--color-on-surface);
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border-subtle);
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border-subtle);
}
.section-title {
  flex-shrink: 0;
  margin: 0;
  font: 600 var(--text-headline-sm)/1.4 var(--font-headline);
  color: var(--color-on-surface);
}
.discussion-heading-main {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px 20px;
}
.discussion-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}
.discussion-filters button {
  padding: 7px 10px;
  border-radius: var(--radius-md);
  color: var(--color-on-surface-variant);
  white-space: nowrap;
}
.discussion-filters button:hover {
  background: var(--color-surface-container-low);
}
.discussion-filters button.selected {
  color: var(--color-primary);
  background: var(--color-surface-container-low);
  font-weight: 600;
}
.discussion-filters button:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 3px;
}
.section-actions {
  display: flex;
  align-items: center;
  gap: 18px;
}
.section-link {
  color: var(--color-primary);
  font-weight: 500;
}
.section-link:hover {
  text-decoration: underline;
}
.section-action {
  padding: 7px 14px;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-weight: 500;
}
.plan-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding-top: 16px;
}
.plan-filters button {
  padding: 7px 12px;
  border-radius: var(--radius-md);
  color: var(--color-on-surface-variant);
}
.plan-filters button.selected {
  background: var(--color-surface-container-low);
  color: var(--color-primary);
  font-weight: 600;
}
.plan-total {
  margin-left: auto;
  color: var(--color-on-surface-variant);
  font-size: var(--text-label-sm);
}
.section-status,
.section-empty {
  padding: 30px 16px;
  text-align: center;
  color: var(--color-on-surface-variant);
}
.section-empty {
  display: grid;
  justify-items: center;
  gap: 10px;
}
.section-empty p {
  color: var(--color-on-surface);
  font-weight: 500;
}
.section-empty > span {
  color: var(--color-outline);
}
.section-error {
  color: var(--color-error);
}
.retry-button {
  margin-top: 12px;
  color: var(--color-primary);
  text-decoration: underline;
}
.preview-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.preview-list li + li {
  border-top: 1px solid var(--color-border-subtle);
}
.preview-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 8px;
  border-radius: 6px;
}
.preview-row:hover {
  background: var(--color-surface-container-low);
}
.preview-main {
  flex: 1;
  min-width: 0;
}
.preview-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  margin-bottom: 7px;
}
.preview-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-on-surface);
  font-weight: 600;
  font-size: var(--text-body-lg);
}
.item-badge {
  flex-shrink: 0;
  padding: 1px 7px;
  border-radius: 5px;
  background: var(--color-surface-container-low);
  color: var(--color-on-surface-variant);
  font-size: var(--text-label-sm);
}
.pinned-badge {
  color: #986500;
  background: #fff5da;
}
.preview-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 16px;
  color: var(--color-on-surface-variant);
  font-size: var(--text-label-sm);
}
.related-problem {
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.plan-status {
  flex-shrink: 0;
  font-size: var(--text-label-sm);
  color: var(--color-on-surface-variant);
}
.plan-status.ongoing {
  color: var(--color-primary);
}
.plan-status.not_started {
  color: #9a6900;
}
.plan-status.ended,
.plan-status.inactive {
  color: var(--color-outline);
}
.row-inactive .preview-title {
  color: var(--color-outline);
}
.post-counts {
  display: flex;
  flex-shrink: 0;
  gap: 16px;
  color: var(--color-on-surface-variant);
  font-size: var(--text-label-sm);
}
.section-action:focus-visible,
.section-link:focus-visible,
.preview-row:focus-visible,
.plan-filters button:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 3px;
}
@media (max-width: 600px) {
  .home-card {
    padding: 20px 16px;
  }
  .preview-row {
    align-items: flex-start;
    flex-wrap: wrap;
    gap: 10px;
  }
  .preview-title {
    font-size: var(--text-body-md);
  }
  .preview-meta {
    gap: 5px 10px;
  }
  .post-counts {
    width: 100%;
  }
  .section-actions {
    gap: 12px;
  }
}

.quick-links {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}

.quick-link {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  font-size: var(--text-body-md);
  font-weight: 500;
  color: var(--color-on-surface);
  transition:
    box-shadow 0.2s,
    border-color 0.2s;
}

.quick-link:hover {
  box-shadow: var(--shadow-sm);
}

.link-icon {
  font-size: 20px;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  background: var(--color-surface-container-low);
}
</style>
