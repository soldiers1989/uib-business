/**
 * Copyright &copy; 2014-2016  All rights reserved.
 *
 * Licensed under the 深圳嘉宝易汇通科技发展有限公司 License, Version 1.0 (the "License");
 * 
 */
package com.uib.quartz;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.uib.common.enums.OrderStatus;
import com.uib.member.service.MemMemberService;
import com.uib.order.dao.OrderDao;
import com.uib.order.entity.OrderTable;
import com.uib.order.entity.OrderTableItem;
import com.uib.order.service.OrderService;
import com.uib.order.service.impl.OrderServiceImpl;
import com.uib.product.dao.ProductServiceDao;

/**
 * @ClassName: OrderTimetask
 * @Description:处理订单
 * @author sl
 * @date 2015年10月24日 上午10:23:58
 */
@Component
public class OrderTableTimeTask {
	private final Logger logger = Logger.getLogger(OrderTableTimeTask.class);
	@Autowired
	private OrderDao orderDao;

	@Autowired
	private ProductServiceDao productServiceDao;

	@Autowired
	private OrderService orderService;
	
	@Autowired
	private MemMemberService memMemberService;
	

	// 用户未点击“确认收货”，10天后系统自动处理订单为已签收状态

	public void updateOrder() {
		logger.info("用户未点击“确认收货”，10天后系统自动处理订单为已签收状态job开始");
		long startTime = System.currentTimeMillis();
		try {
			List<Object> list = orderDao.findIdByOrderStatus(OrderStatus.shipped.getIndex());
			if (list.size() > 0) {
				orderDao.updateOrderStatus(list);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		long endTime = System.currentTimeMillis();
		logger.info("用户未点击“确认收货”，10天后系统自动处理订单为已签收状态job结束");
		logger.info("用户未点击“确认收货”，10天后系统自动处理订单为已签收状态总耗时===="+(endTime-startTime)/1000);
	}

	/**
	 * 提交订单 未支付, 超过半小时 取消订单,并且计算已分配的商品库存
	 */
	private void updateCancelOrder() {
		logger.info("超过半小时 取消订单,并且计算已分配的商品库存job开始");
		try {
			List<OrderTable> orderTableList = orderService.getOrderWaitPayStatusTimeout();
			if (null != orderTableList && orderTableList.size() > 0) {
				Map<String, Object> paramMap = new HashMap<String, Object>();
				paramMap.put("orderStatus", OrderStatus.cancelled.getIndex());
				paramMap.put("list", orderTableList);
				//更新订单状态为已取消
				orderDao.updateOrderStatusByOrderStatusAndIds(paramMap);
				//更新已分配的库存
				for (OrderTable order : orderTableList) {
//					memMemberService.updateIsUsedByCode(order.getCouponCode(), IsUsedStates.Unused.getIndex());
					List<OrderTableItem> orderItemList = order.getList_ordertable_item();
					if (null == orderItemList) {
						continue;
					}
					for (OrderTableItem orderItem : orderItemList) {
						Map<String, Object> map = new HashMap<String, Object>();
						map.put("allocatedStock", orderItem.getQuantity());
						map.put("id", orderItem.getGoodsNo());
						productServiceDao.updateSubtractAllocatedStock(map);
					}
				}
			}

		} catch (Exception ex) {
			ex.printStackTrace();
		}
		logger.info("超过半小时 取消订单,并且计算已分配的商品库存job结束");
	}

	public void init() {
		updateOrder();
		updateCancelOrder();
	}
}
