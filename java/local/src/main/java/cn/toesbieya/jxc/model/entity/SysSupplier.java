package cn.toesbieya.jxc.model.entity;

import lombok.Data;

@Data
public class SysSupplier {
    private Integer id;
    private String name;
    private String address;
    private String linkman;
    private String linkphone;
    private String region;
    private boolean enable = false;
    private Long ctime;
    private String remark;
}
