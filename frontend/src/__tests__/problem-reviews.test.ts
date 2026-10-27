import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import Reviews from '@/components/problem/ProblemReviews.vue'
const api=vi.hoisted(()=>({pending:vi.fn(),history:vi.fn(),decide:vi.fn()}))
vi.mock('@/api/problems',()=>({getPendingReviews:api.pending,getProblemReviews:api.history,decideProblemReview:api.decide}))
beforeEach(()=>{vi.clearAllMocks();api.pending.mockResolvedValue([{id:1,problem_id:7,author_user_id:2,proposed_content:'{"title":"修改题目"}',status:'PENDING'}]);api.history.mockResolvedValue([]);api.decide.mockResolvedValue(undefined)})
describe('题目审核',()=>{
 it('管理员可以查看建议并审核',async()=>{const pinia=createPinia();useAuthStore(pinia).user={id:1,username:'admin',nickname:'管理员',email:null,avatarUrl:null,bio:null,admin:true};const w=mount(Reviews,{props:{pending:true},global:{plugins:[pinia]}});await flushPromises();expect(w.text()).toContain('待审核');expect(w.text()).toContain('修改题目');await w.get('[aria-label="审核意见"]').setValue('同意');await w.get('.actions button').trigger('click');await flushPromises();expect(api.decide).toHaveBeenCalledWith(1,'approve','同意');w.unmount()})
 it('普通用户只获取自己的建议历史',async()=>{const pinia=createPinia();useAuthStore(pinia).user={id:2,username:'member',nickname:'用户',email:null,avatarUrl:null,bio:null,admin:false};const w=mount(Reviews,{props:{problemId:7},global:{plugins:[pinia]}});await flushPromises();expect(api.history).toHaveBeenCalledWith(7);expect(w.find('.actions').exists()).toBe(false);w.unmount()})
})
