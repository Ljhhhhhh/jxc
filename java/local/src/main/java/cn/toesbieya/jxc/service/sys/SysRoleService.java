package cn.toesbieya.jxc.service.sys;

import cn.toesbieya.jxc.annoation.UserAction;
import cn.toesbieya.jxc.mapper.SysRoleMapper;
import cn.toesbieya.jxc.model.entity.SysRole;
import cn.toesbieya.jxc.model.vo.R;
import cn.toesbieya.jxc.model.vo.result.PageResult;
import cn.toesbieya.jxc.model.vo.search.RoleSearch;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.List;

@Service
public class SysRoleService {
    @Resource
    private SysRoleMapper mapper;

    public List<SysRole> get() {
        return mapper.selectList(
                Wrappers.lambdaQuery(SysRole.class)
                        .eq(SysRole::isEnable, true)
        );
    }

    public SysRole getRoleById(Integer id) {
        return id == null ? null : mapper.selectById(id);
    }

    public PageResult<SysRole> search(RoleSearch vo) {
        Integer id = vo.getId();
        String name = vo.getName();
        Integer cid = vo.getCid();
        String cname = vo.getCname();
        Boolean enable = vo.getEnable();
        Long startTime = vo.getStartTime();
        Long endTime = vo.getEndTime();

        Wrapper<SysRole> wrapper =
                Wrappers.lambdaQuery(SysRole.class)
                        .eq(id != null, SysRole::getId, id)
                        .like(!StringUtils.isEmpty(name), SysRole::getName, name)
                        .eq(cid != null, SysRole::getCid, cid)
                        .like(!StringUtils.isEmpty(cname), SysRole::getName, cname)
                        .eq(enable != null, SysRole::isEnable, enable)
                        .ge(startTime != null, SysRole::getCtime, startTime)
                        .le(endTime != null, SysRole::getCtime, endTime)
                        .orderByDesc(SysRole::getCtime);

        PageHelper.startPage(vo.getPage(), vo.getPageSize());
        return new PageResult<>(mapper.selectList(wrapper));
    }

    @UserAction("'添加角色：'+#role.name")
    public R add(SysRole role) {
        if (isNameExist(role.getName(), null)) {
            return R.fail("添加失败，角色名称重复");
        }
        int rows = mapper.insert(role);
        return rows > 0 ? R.success("添加成功") : R.fail("添加失败");
    }

    @UserAction("'修改角色：'+#role.name")
    public R update(SysRole role) {
        Integer id = role.getId();
        String name = role.getName();

        if (isNameExist(name, id)) {
            return R.fail("修改失败，角色名称重复");
        }

        mapper.update(
                null,
                Wrappers.lambdaUpdate(SysRole.class)
                        .set(SysRole::getName, name)
                        .set(SysRole::isEnable, role.isEnable())
                        .set(SysRole::getScope, role.getScope())
                        .set(SysRole::getDepartmentId, role.getDepartmentId())
                        .set(SysRole::getResourceId, role.getResourceId())
                        .eq(SysRole::getId, id)
        );
        return R.success("修改成功");
    }

    @UserAction("'删除角色：'+#role.name")
    public R del(SysRole role) {
        int rows = mapper.delete(
                Wrappers.lambdaQuery(SysRole.class)
                        .eq(SysRole::getId, role.getId())
                        .eq(SysRole::isEnable, false)
        );
        return rows > 0 ? R.success("删除成功") : R.fail("删除失败，请刷新重试");
    }

    private boolean isNameExist(String name, Integer id) {
        Integer num = mapper.selectCount(
                Wrappers.lambdaQuery(SysRole.class)
                        .eq(SysRole::getName, name)
                        .ne(id != null, SysRole::getId, id)
        );

        return num != null && num > 0;
    }
}
