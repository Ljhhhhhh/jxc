package cn.toesbieya.jxc.account.controller;

import cn.toesbieya.jxc.account.service.AccountService;
import cn.toesbieya.jxc.account.vo.LoginParam;
import cn.toesbieya.jxc.account.vo.PasswordUpdateParam;
import cn.toesbieya.jxc.account.vo.RegisterParam;
import cn.toesbieya.jxc.common.model.entity.SysUser;
import cn.toesbieya.jxc.common.model.vo.R;
import cn.toesbieya.jxc.common.model.vo.UserVo;
import cn.toesbieya.jxc.web.common.util.IpUtil;
import cn.toesbieya.jxc.web.common.util.SessionUtil;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

@RestController
@RequestMapping("account")
public class AccountController {
    @Resource
    private AccountService service;

    @PostMapping("login")
    public R login(HttpServletRequest request, @Valid @RequestBody LoginParam param) {
        return service.login(param, IpUtil.getIp(request));
    }

    @GetMapping("logout")
    public R logout(HttpServletRequest request) {
        UserVo user = SessionUtil.get();
        return service.logout(user, IpUtil.getIp(request));
    }

    @PostMapping("register")
    public R register(@Valid @RequestBody RegisterParam param) {
        return service.register(param);
    }

    @PostMapping("updatePwd")
    public R updatePwd(@RequestBody PasswordUpdateParam param) {
        SysUser user = SessionUtil.get();
        param.setId(user.getId());

        String errMsg = validateUpdatePwdParam(param);
        if (errMsg != null) return R.fail(errMsg);

        return service.updatePwd(param);
    }

    @GetMapping("updateAvatar")
    public R updateAvatar(@RequestParam String key) throws UnsupportedEncodingException {
        if (StringUtils.isEmpty(key)) return R.fail("参数错误");

        return service.updateAvatar(SessionUtil.get(), URLDecoder.decode(key, "utf-8"));
    }

    @GetMapping("validate")
    public R validate(@RequestParam String pwd) {
        SysUser current = SessionUtil.get();

        if (!pwd.equals(current.getPwd())) {
            return R.fail("校验失败");
        }

        return R.success("校验通过");
    }

    @GetMapping("checkLoginName")
    public R checkLoginName(@RequestParam(required = false) Integer id, @RequestParam String name) {
        if (StringUtils.isEmpty(name)) {
            return R.success();
        }

        return R.success(service.isLoginNameExist(name, id) ? "该登录名已存在" : null);
    }

    @GetMapping("checkNickName")
    public R checkNickName(@RequestParam(required = false) Integer id, @RequestParam String name) {
        if (StringUtils.isEmpty(name)) {
            return R.success();
        }

        return R.success(service.isNickNameExist(name, id) ? "该昵称已存在" : null);
    }

    private String validateUpdatePwdParam(PasswordUpdateParam vo) {
        if (vo.getId() == null) return "修改失败，参数错误";
        if (StringUtils.isEmpty(vo.getOldPwd())) return "修改失败，原密码不能为空";
        if (StringUtils.isEmpty(vo.getNewPwd())) return "修改失败，新密码不能为空";
        if (vo.getOldPwd().equals(vo.getNewPwd())) return "修改失败，新密码不得与旧密码相同";
        if (vo.getNewPwd().length() != 32) return "修改失败，密码参数有误";
        return null;
    }
}
