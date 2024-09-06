package cn.toesbieya.jxc.model.vo.search;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class RoleSearch extends BaseSearch {
    private Integer id;
    private String name;
    private Integer cid;
    private String cname;
    private Boolean enable;
    private Long startTime;
    private Long endTime;
}
