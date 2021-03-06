/**
 * Copyright &copy; 2012-2014 <a href="https://github.com/thinkgem/uib_ec">uib_ec</a> All rights reserved.
 */
package com.uib.ecmanager.modules.order.dao;

import com.uib.ecmanager.common.persistence.CrudDao;
import com.uib.ecmanager.common.persistence.annotation.MyBatisDao;
import com.uib.ecmanager.modules.order.entity.OrderTableReturns;

/**
 * 退货单DAO接口
 * @author limy
 * @version 2015-06-08
 */
@MyBatisDao
public interface OrderTableReturnsDao extends CrudDao<OrderTableReturns> {

	/**
	 * @param orderTableReturns
	 */
	void updateStatus(OrderTableReturns orderTableReturns);
	
}