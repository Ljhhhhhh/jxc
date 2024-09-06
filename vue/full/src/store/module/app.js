import { createMutations } from '@/store/util';

const state = {};

const mutations = createMutations(state);

export default {
  namespaced: true,
  state,
  mutations,
};
