package cn.toesbieya.jxc.model.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class BizPurchaseInbound extends BizDoc {
    private String pid;
}
