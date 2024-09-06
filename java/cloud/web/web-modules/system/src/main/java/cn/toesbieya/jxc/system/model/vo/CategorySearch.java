package cn.toesbieya.jxc.system.model.vo;

import lombok.Data;

@Data
public class CategorySearch {
    private Integer id;
    private String ids;
    private Integer pid;
    private String pids;
    private String name;
    private Boolean leaf;
    private Long startTime;
    private Long endTime;
}
