package cn.toesbieya.jxc.web.common.util;

import cn.toesbieya.jxc.common.model.vo.UserVo;

public class SessionUtil extends cn.toesbieya.jxc.common.util.SessionUtil {
    public static UserVo get() {
        UserVo user = ThreadUtil.getUser();
        return user == null ? get(WebUtil.getRequest()) : user;
    }
}
