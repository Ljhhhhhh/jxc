<template>
  <div class="login-page">
    <div class="login-container" @click.stop>
      <div class="title">
        {{ title }}
      </div>

      <component :is="component" />
    </div>
  </div>
</template>

<script>
import { mapState } from 'vuex';
import { title } from '@/config';
import LoginForm from './LoginForm';
import RegisterForm from './RegisterForm';
import { isEmpty } from '@/util';
import { isMobile } from '@/util/browser';

export default {
  name: 'login',

  components: { LoginForm, RegisterForm },

  data: () => ({ title }),

  computed: {
    component() {
      const formType = this.$route.path.substring(1);
      return `${[...formType].join('')}-form`;
    },
  },

  methods: {},

  mounted() {},

  beforeDestroy() {
    this.$message.closeAll();
  },
};
</script>

<style lang="scss" src="./style.scss"></style>
