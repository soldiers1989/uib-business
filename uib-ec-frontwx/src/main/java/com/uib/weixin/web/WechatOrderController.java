package com.uib.weixin.web;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.collections.ListUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.support.RequestContext;

import com.uib.cart.entity.Cart;
import com.uib.cart.entity.CartItem;
import com.uib.cart.service.CartService;
import com.uib.common.enums.ExceptionEnum;
import com.uib.common.enums.IsUsedStates;
import com.uib.common.enums.OrderStatus;
import com.uib.common.mapper.JsonMapper;
import com.uib.common.utils.Des;
import com.uib.common.utils.DigestUtil;
import com.uib.common.utils.JacksonUtil;
import com.uib.common.utils.StringUtils;
import com.uib.member.entity.MemMember;
import com.uib.member.service.MemMemberService;
import com.uib.mobile.dto.OrderCouponDto;
import com.uib.mobile.dto.OrderItemDto;
import com.uib.mobile.dto.OrderItemPojo4Mobile;
import com.uib.mobile.dto.OrderPojo4Mobile;
import com.uib.mobile.dto.OrderTableDto;
import com.uib.mobile.dto.ReturnMsg;
import com.uib.order.dao.OrderShippingRefDao;
import com.uib.order.entity.BalancePayLog;
import com.uib.order.entity.OrderTable;
import com.uib.order.entity.OrderTableItem;
import com.uib.order.service.BalancePayLogService;
import com.uib.order.service.OrderService;
import com.uib.order.service.OrderTableShippingService;
import com.uib.product.entity.Product;
import com.uib.product.service.ProductService;
import com.uib.serviceUtils.Utils;
import com.uib.weixin.constant.WxConstant;
import com.uib.weixin.util.UserSession;

/**
 * 手机
 * 
 * @author gaven
 * 
 */
@Controller
@RequestMapping("/wechat/member/order")
public class WechatOrderController {

	private Logger logger = LoggerFactory.getLogger("rootLogger");

	@Autowired
	private OrderService orderService;
	@Autowired
	private CartService cartService;
	@Autowired
	private ProductService productService;
	@Autowired
	private MemMemberService memMemberService;
	@Autowired
	private OrderTableShippingService orderShippingService;
	@Autowired
	private OrderShippingRefDao orderShippingRefDao;
	
	@Autowired
	private BalancePayLogService balancePayLogService;

	/***
	 * 保存订单
	 * @param order
	 * @param receiverId
	 * @param cartId
	 * @param pid
	 * @param balanceFlag
	 * @param balancePwd
	 * @param request
	 * @return
	 */
	@RequestMapping("/add")
	@ResponseBody
	public ReturnMsg<OrderPojo4Mobile> addOrder(OrderTable order, String receiverId, String cartId, String[] pid, String balanceFlag, String balancePwd, HttpServletRequest request) {
		ReturnMsg<OrderPojo4Mobile> msg = new ReturnMsg<OrderPojo4Mobile>();
		RequestContext requestContext = new RequestContext(request);
		try {
			logger.info("========================receiverId:" + receiverId + ",cartId" + cartId + ",pid:" + JsonMapper.toJsonString(pid) + ",order:" + JsonMapper.toJsonString(order));
			if (Utils.isObjectsBlank(cartId, pid, order.getPaymentMethod())) {
				msg.setCode(ExceptionEnum.param_not_null.getIndex());
				msg.setStatus(false);
				msg.setMsg(requestContext.getMessage("business.param.notNull"));
				return msg;
			}
			if(StringUtils.isEmpty(receiverId)){
				if(StringUtils.isEmpty(order.getConsignee()) || StringUtils.isEmpty(order.getAreaName()) 
						|| StringUtils.isEmpty(order.getAddress()) || StringUtils.isEmpty(order.getPhone()) || StringUtils.isEmpty(order.getArea())){
					msg.setCode(ExceptionEnum.param_not_null.getIndex());
					msg.setStatus(false);
					msg.setMsg(requestContext.getMessage("business.param.notNull"));
					return msg;
				}
			}
			Cart cart = cartService.selectCartById(cartId);
			//验证购物车
			msg = checkCart(cartId, pid, cart);
			if(!msg.isStatus()){
				logger.info("返回参数:" + JacksonUtil.writeValueAsString(msg));
				return msg;
			}
			//验证商品库存、上下架状态、价格是否改动
			msg = checkProduct(pid, cart.getCartItems());
			if(!msg.isStatus()){
				logger.info("返回参数:" + JacksonUtil.writeValueAsString(msg));
				return msg;
			}
			//如果选择余额支付，需要验证支付密码
			if("1".equals(balanceFlag)){
				if(StringUtils.isEmpty(balancePwd)){
					msg.setCode(ExceptionEnum.pass_error.getIndex());
					msg.setStatus(false);
					msg.setMsg(ExceptionEnum.pass_error.getValue());
					return msg;
				}
				Des desObj = new Des();
				balancePwd = desObj.strDec(balancePwd, "1", "2", "3");
				
				MemMember member = memMemberService.getMemMember(String.valueOf(UserSession.getSession(WxConstant.wx_user_id)));
				if(!DigestUtil.MD5(balancePwd).equals(member.getPayPassword())){
					msg.setCode(ExceptionEnum.pass_error.getIndex());
					msg.setStatus(false);
					msg.setMsg(ExceptionEnum.pass_error.getValue());
					return msg;
				}
			}
			//保存订单信息  0 = 未开启余额支付,1 = 余额不足,2 = 余额充足, -1 = 扣款失败 
			int result = orderService.addOrderByWechat(order, cart, receiverId, pid, balanceFlag);
			
			OrderPojo4Mobile orderPojo4Mobile = new OrderPojo4Mobile();
			BeanUtils.copyProperties(orderPojo4Mobile, order);
			List<OrderItemPojo4Mobile> itemList = new ArrayList<OrderItemPojo4Mobile>();
			List<OrderTableItem> orderItems = order.getList_ordertable_item();
			for (OrderTableItem orderTableItem : orderItems) {
				OrderItemPojo4Mobile it = new OrderItemPojo4Mobile();
				BeanUtils.copyProperties(it, orderTableItem);
				itemList.add(it);
			}
			orderPojo4Mobile.setList_ordertable_item(itemList);
			msg.setStatus(true);
			msg.setData(orderPojo4Mobile);
			orderPojo4Mobile.setWxPayFlag(result);
			//orderPojo4Mobile.setWxPayPrice(order.getTotalPrice().subtract(new BigDecimal(order.getAmountPaid())));
		} catch (Exception e) {
			logger.error("保存订单出错！", e);
			msg.setStatus(false);
			msg.setCode(ExceptionEnum.business_logic_exception.getIndex());
			msg.setMsg(requestContext.getMessage("business.logic.exception"));
		}
		return msg;
	}

	private ReturnMsg<OrderPojo4Mobile> checkCart(String cartId, String[] pid, Cart cart) {
		ReturnMsg<OrderPojo4Mobile> msg = new ReturnMsg<OrderPojo4Mobile>();
		if (Utils.isBlank(cart)) {
			msg.setStatus(false);
			msg.setCode(ExceptionEnum.param_illegal.getIndex());
			msg.setMsg(ExceptionEnum.param_illegal.getValue()+"cartId:" + cartId + ",购物车不存在.");
			return msg;
		}
		List<CartItem> cartItems = cart.getCartItems();
		if (Utils.isBlank(cartItems)) {
			msg.setStatus(false);
			msg.setCode(ExceptionEnum.cartItems_is_blank.getIndex());
			msg.setMsg(ExceptionEnum.cartItems_is_blank.getValue());
			return msg;
		}
		List<String> correctIds = new ArrayList<String>();
		List<String> idList = Arrays.asList(pid);
		for (CartItem item : cartItems) {
			String productId = item.getProductId();
			if (idList.contains(productId)) {
				correctIds.add(productId);
			}
		}
		if (Utils.isBlank(correctIds) || correctIds.size() < pid.length) {
			msg.setCode(ExceptionEnum.param_illegal.getIndex());
			msg.setStatus(false);
			msg.setMsg(ExceptionEnum.param_illegal.getValue() + ",购物车不存在的商品id列表:" + ListUtils.subtract(idList, correctIds));
			return msg;
		}
		msg.setStatus(true);
		return msg;
	}
	
	private ReturnMsg<OrderPojo4Mobile> checkProduct(String[] pid, List<CartItem> cartItems) {
		ReturnMsg<OrderPojo4Mobile> result = new ReturnMsg<OrderPojo4Mobile>();
		Product p = null;
		for (CartItem item : cartItems) {
			for(String productId : pid){
				if(!productId.equals(item.getProductId())){
					continue;
				}
				p = productService.findById(item.getProductId());
				// 判断商品是否为空
				if (Utils.isBlank(p)) {
					result.setCode(ExceptionEnum.product_not_exist.getIndex());
					result.setStatus(false);
					result.setMsg(ExceptionEnum.product_not_exist.getValue());
					return result;
				}
				// 判断商品是否下架
				if (p.getWxIsMarketable().equals("0")) {
					result.setCode(ExceptionEnum.product_undercarriage.getIndex());
					result.setStatus(false);
					result.setMsg(ExceptionEnum.product_undercarriage.getValue());
					return result;
				}
				//可用库存 = 库存 - 已分配库存
				int restStock = p.getStock() - p.getAllocatedStock();
				// 判断库存是否不足
				if (item.getQuantity() > restStock) {
					result.setCode(ExceptionEnum.product_Low_stock.getIndex());
					result.setStatus(false);
					result.setMsg(ExceptionEnum.product_Low_stock.getValue());
					return result;
				}
				BigDecimal pPrice = new BigDecimal(p.getPrice());
				int ct = pPrice.compareTo(new BigDecimal(item.getProductPrice()));
				
				// 判断商品价格是否改动
				if (ct != 0) {
					result.setCode(ExceptionEnum.product_price_different
							.getIndex());
					result.setStatus(false);
					result.setMsg(ExceptionEnum.product_price_different.getValue());
					return result;
				}
			}
		}
		result.setStatus(true);
		return result;
	}
	
	
	/**
	 * 查询订单
	 * 
	 * @param order
	 * @param request
	 * @return
	 */
	@RequestMapping("query")
	@ResponseBody
	public ReturnMsg<List<OrderPojo4Mobile>> queryOrders(OrderTable order,
			HttpServletRequest request) {
		logger.info(JsonMapper.toJsonString(order));
		ReturnMsg<List<OrderPojo4Mobile>> msg = new ReturnMsg<List<OrderPojo4Mobile>>();
		RequestContext requestContext = new RequestContext(request);
		String userName = (String)UserSession.getSession(WxConstant.wx_user_name);
		try {
			// 非空判断
			if (Utils.isBlank(order) || Utils.isBlank(userName)) {
				msg.setCode(ExceptionEnum.param_not_null.getIndex());
				msg.setStatus(false);
				msg.setMsg(requestContext.getMessage("business.param.notNull")
						+ ":userName");
				return msg;
			}
			
			/*if(Utils.isBlank(userName)){
				msg.setCode(ExceptionEnum.param_illegal.getIndex());
				msg.setStatus(false);
				msg.setMsg(requestContext.getMessage("business.param.illegal")
						+ ",该用户不存在,userName:" + userName);
				return msg;
			}*/
			MemMember member = memMemberService.getCurrentUser(userName);
			// 用户名是否存在判断
			if (Utils.isBlank(member)) {
				
				msg.setCode(ExceptionEnum.param_illegal.getIndex());
				msg.setStatus(false);
				msg.setMsg(requestContext.getMessage("business.param.illegal")
						+ ",该用户不存在,userName:" + userName);
				return msg;
			}
			
			order.setUserName(userName);
			List<OrderPojo4Mobile> orders = orderService
					.queryOrderPojo4Mobiles(order);
			msg.setStatus(true);
			msg.setData(orders);
		} catch (Exception e) {
			logger.error("查询订单报错！", e);
			msg.setStatus(false);
			msg.setCode(ExceptionEnum.business_logic_exception.getIndex());
			msg.setMsg(requestContext.getMessage("business.logic.exception"));
		}
		return msg;
	}
	
	/**
	 * 统计待收货订单
	 * @return
	 */
	@RequestMapping("/queryOrderDeliveryCount/status")
	@ResponseBody
	public int queryOrderDeliveryCount() {
		int count = 0;
		try {
			String userName = (String)UserSession.getSession(WxConstant.wx_user_name);
			if (StringUtils.isEmpty(userName)) {
				return count;
			}
			String orderStatus = OrderStatus.shipped.getIndex();
			count = orderService.queryOrderDeliveryCount(userName,orderStatus);
		} catch (Exception e) {
			logger.info("统计待收货订单异常"+e);
		}
		return count;
	}
	/**
	 * 统计待付款和已付款 等待发货订单
	 * @return
	 */
	@RequestMapping("/queryOrderCountByStatus")
	@ResponseBody
	public ReturnMsg<Integer> queryOrderCountByStatus(String status) {
		ReturnMsg<Integer> msg = new ReturnMsg<Integer>();
		logger.info("订单状态status:"+status);
		if (Utils.isObjectsBlank(status)){
			msg.setCode(ExceptionEnum.param_not_null.getIndex());
			msg.setStatus(false);
			msg.setMsg(ExceptionEnum.param_not_null.getValue());
			return msg;
		} 
		String userName = String.valueOf(UserSession.getSession(WxConstant.wx_user_name));
		int count = 0;
		try{
		    count = orderService.queryOrderDeliveryCount(userName,status);
		}catch (Exception e) {
			logger.error("查询出错，错误信息:{}", e.getMessage());
		}
		msg.setStatus(true);
		msg.setData(count);
		return msg;
	}

	/**
	 * 更新订单状态
	 * 
	 * @param order
	 * @param request
	 * @return
	 */
	@RequestMapping("/update/status")
	@ResponseBody
	public ReturnMsg<Object> updateOrderStatus(OrderTable order,
			HttpServletRequest request) {
		ReturnMsg<Object> result = new ReturnMsg<Object>();
		RequestContext requestContext = new RequestContext(request);
		try {
			// 非空判断
			if (Utils.isBlank(order)
					|| Utils.isObjectsBlank(order.getOrderNo(),
							order.getOrderStatus())) {
				result.setCode(ExceptionEnum.param_not_null.getIndex());
				result.setStatus(false);
				result.setMsg(requestContext
						.getMessage("business.param.notNull")
						+ ":orderNo,orderStatus");
				return result;
			}
			String orderNo = order.getOrderNo();
			String status = order.getOrderStatus();
			if (!status.matches("^\\d$")
					|| !OrderStatus.CheckOrderStatusCode(status)) {
				result.setCode(ExceptionEnum.param_illegal.getIndex());
				result.setStatus(false);
				result.setMsg(requestContext
						.getMessage("business.param.illegal") + ",订单状态非法");
				return result;
			}
			// 订单是否存在、可修改判断
			OrderTable orderTable = orderService.getOrderByOrderNo(orderNo);
			if (Utils.isBlank(orderTable)) {
				result.setCode(ExceptionEnum.order_not_exist.getIndex());
				result.setStatus(false);
				result.setMsg(requestContext.getMessage("order.notExist"));
				return result;
			}
			OrderStatus orderStatus = OrderStatus.getStatusByIndex(orderTable
					.getOrderStatus());
			OrderStatus updateStatus = OrderStatus.getStatusByIndex(status);
			switch (orderStatus) {
			// 可取消订单
			case unconfirmed:
			case wait_pay:
				if (!OrderStatus.cancelled.equals(updateStatus)) {
					result.setCode(ExceptionEnum.param_illegal.getIndex());
					result.setStatus(false);
					result.setMsg(requestContext
							.getMessage("business.param.illegal") + ",订单状态参数错误");
				} else {
					orderService.updateByOrderNO(orderNo, updateStatus.getIndex());
					memMemberService.updateIsUsedByCode(orderTable.getCouponCode(),
							IsUsedStates.Unused.getIndex());
					// 删除发货信息
					Map<String, String> params = new HashMap<String, String>();
					params.put("orderNo", orderNo);
					orderShippingRefDao.delete(params);
					orderShippingService.deleteOrderTableShipping(params);
					
					BalancePayLog balancePayLog = balancePayLogService.getPayLogByOrderNo(orderNo);
					MemMember member = memMemberService.getMemMember(String.valueOf(String.valueOf(UserSession.getSession(WxConstant.wx_user_id))));
					
					//将该账户已冻结状态的订单余额进行退款
					if(balancePayLog != null && "0".equals(balancePayLog.getStatus()) && member.getUsername().equals(balancePayLog.getUserName())){
						logger.debug("用户取消订单，即将进行退款...orderNo = ", orderNo);
						//修改用户账户余额
						Map<String, String> param = new HashMap<String, String>();
						param.put("balance", balancePayLog.getAmount().add(new BigDecimal(member.getBalance())).toString());
						param.put("id", member.getId());
						memMemberService.updateMemberInfoById(param);
						
						balancePayLog.setUpdateDate(new Date());
						balancePayLog.setStatus("2");//已退款
						balancePayLogService.updatePayLog(balancePayLog);
						
						logger.debug("退款成功，订单号：{}, 退款金额{}...", orderNo, balancePayLog.getAmount().toString());
					}
					result.setStatus(true);
				}
				break;
			// 可确认收货
			case paid_shipped:
			case shipped:
				if (!OrderStatus.confirmed.equals(updateStatus)) {
					result.setCode(ExceptionEnum.param_illegal.getIndex());
					result.setStatus(false);
					result.setMsg(requestContext
							.getMessage("business.param.illegal")
							+ ",订单不可修改为确认状态");
				} else {
					orderService.updateByOrderNO(orderNo,
							OrderStatus.completed.getIndex());
					//更新笔订单商品的销量
					orderService.updateOrderByProductSales(order.getOrderNo());
				}
				result.setStatus(true);
				break;
			// 可删除,修改del_flag
			// case completed:
			// case cancelled:
			// break;
			default:
				result.setCode(ExceptionEnum.param_illegal.getIndex());
				result.setStatus(false);
				result.setMsg(requestContext
						.getMessage("business.param.illegal")
						+ ",订单不可修改成该状态,orderNo:" + orderNo);
				break;
			}
			return result;
		} catch (Exception e) {
			logger.error("修改订单状态出错!", e);
			result.setStatus(false);
			result.setCode(ExceptionEnum.business_logic_exception.getIndex());
			result.setMsg(requestContext.getMessage("business.logic.exception"));
			return result;
		}

	}

	/**
	 * 根据订单号删除订单
	 * 
	 * @param orderNo
	 * @param request
	 * @return
	 */
	@RequestMapping("/delete")
	@ResponseBody
	public ReturnMsg<Object> delete(String orderNo, HttpServletRequest request) {
		ReturnMsg<Object> result = new ReturnMsg<Object>();
		RequestContext requestContext = new RequestContext(request);
		try {
			if (Utils.isBlank(orderNo)) {
				result.setCode(ExceptionEnum.param_not_null.getIndex());
				result.setStatus(false);
				result.setMsg(requestContext
						.getMessage("business.param.notNull") + ":orderNo");
				return result;
			}
			// 订单是否存在、可修改判断
			OrderTable orderTable = orderService.getOrderByOrderNo(orderNo);
			if (Utils.isBlank(orderTable)) {
				result.setCode(ExceptionEnum.order_not_exist.getIndex());
				result.setStatus(false);
				result.setMsg(requestContext.getMessage("order.notExist"));
				return result;
			}
			OrderStatus orderStatus = OrderStatus.getStatusByIndex(orderTable
					.getOrderStatus());
			switch (orderStatus) {
			case completed:
			case cancelled:
				orderService.deleteOrderByOrderNo(orderNo);
				result.setStatus(true);
				break;
			default:
				result.setCode(ExceptionEnum.param_illegal.getIndex());
				result.setStatus(false);
				result.setMsg(requestContext
						.getMessage("business.param.illegal")
						+ ",当前订单状态下不可删除,orderNo:" + orderNo);
				break;
			}
			return result;
		} catch (Exception e) {
			logger.error("删除订单出错", e);
			result.setStatus(false);
			result.setCode(ExceptionEnum.business_logic_exception.getIndex());
			result.setMsg(requestContext.getMessage("business.logic.exception"));
			return result;
		}

	}

	/**
	 * 查询订单详情
	 * 
	 * @param orderNo
	 * @param request
	 * @return
	 */
	@RequestMapping("/queryOrder")
	@ResponseBody
	public ReturnMsg<OrderTableDto> queryOrder(String orderNo) {
		ReturnMsg<OrderTableDto> msg = new ReturnMsg<OrderTableDto>();
		OrderTableDto orderDto = new OrderTableDto();
		List<OrderItemDto> orderItemDtos = new ArrayList<OrderItemDto>();
		String userName = (String)UserSession.getSession(WxConstant.wx_user_name);
		try {
			logger.info("=======begin 手机APP调用queryOrder方法接口====================");
			logger.info("=======传参 orderNo=" + orderNo + "===userName="
					+ userName + "=======");
			// 非空判断
			if (Utils.isBlank(orderNo) || Utils.isBlank(userName)) {
				msg.setCode(ExceptionEnum.param_not_null.getIndex());
				msg.setStatus(false);
				msg.setMsg(ExceptionEnum.param_not_null.getValue()
						+ ":userName");
				return msg;
			}
			MemMember member = new MemMember();
			//获取当前用户userName
			if(StringUtils.isNotEmpty(userName)) {
				logger.info("code=======:"+userName);
				member = memMemberService.getMemMemberByUsername(userName);
			}
			//MemMember member = memMemberService.getMemMemberByUsername(userName);
			// 用户名是否存在判断
			if (member == null) {
				msg.setCode(ExceptionEnum.member_not_exist.getIndex());
				msg.setStatus(false);
				msg.setMsg(ExceptionEnum.member_not_exist.getValue() + ":"
						+ userName);
				return msg;
			}
			OrderTable order = orderService.getOrderTableByOrderNo(orderNo,
					userName);
			if (order == null) {
				msg.setCode(ExceptionEnum.order_not_exist.getIndex());
				msg.setStatus(false);
				msg.setMsg(ExceptionEnum.order_not_exist.getValue());
				return msg;
			}
			BeanUtils.copyProperties(orderDto, order);
			Product p = null;
			BigDecimal productAmount = new BigDecimal(0);
			for (OrderTableItem oti : order.getList_ordertable_item()) {
				OrderItemDto orderItemDto = new OrderItemDto();
				p = productService.findById(oti.getGoodsNo());
				if (order.getOrderStatus().equals(
						OrderStatus.wait_pay.getIndex())) {
					if (p != null) {
						BigDecimal pPrice = new BigDecimal(p.getPrice()
								.toString());
						oti.setPrice(pPrice);
						orderService.updateOrderPriceByorderItemNo(oti);
					}
				}
				BeanUtils.copyProperties(orderItemDto, oti);
				orderItemDto.setAllocatedStock(p.getAllocatedStock());
				orderItemDto.setIsMarketable(p.getWxIsMarketable());
				orderItemDto.setGoodNo(p.getId());
				orderItemDtos.add(orderItemDto);
				// 计算商品总额
				BigDecimal quantity = new BigDecimal(oti.getQuantity()
						.toString());
				BigDecimal price = quantity.multiply(oti.getPrice());
				productAmount = productAmount.add(price);
				// 订单总额
			//	BigDecimal priceAmount = order.getOrderAmount(productAmount);
				orderDto.setProductAmount(productAmount);
				orderDto.setList_ordertable_item(orderItemDtos);
			}
			msg.setData(orderDto);
		} catch (Exception e) {
			msg.setStatus(false);
			msg.setCode(ExceptionEnum.business_logic_exception.getIndex());
			msg.setMsg(ExceptionEnum.business_logic_exception.getValue());
			logger.error("查询订单详情出错", e);
		}
		logger.info("返回参数:" + JacksonUtil.writeValueAsString(msg));
		logger.info("=======end 手机APP调用queryOrder方法接口====================");
		return msg;
	}

	/**
	 * 支付提示
	 * 
	 * @param orderNo
	 * @param request
	 * @return
	 */
	@RequestMapping("/paymentCues")
	@ResponseBody
	public ReturnMsg<Object> paymentCues(String orderNo) {
		ReturnMsg<Object> msg = new ReturnMsg<Object>();
		String userName = (String)UserSession.getSession(WxConstant.wx_user_name);
		try {
			logger.info("=======begin 手机APP调用paymentCues方法接口====================");
			logger.info("=======传参 orderNo=" + orderNo + "===userName="
					+ userName + "=======");
			// 非空判断
			if (Utils.isBlank(orderNo) || Utils.isBlank(userName)) {
				msg.setCode(ExceptionEnum.param_not_null.getIndex());
				msg.setStatus(false);
				msg.setMsg(ExceptionEnum.param_not_null.getValue()
						+ ":userName");
				return msg;
			}
			MemMember member = null;
			//获取当前用户userName
			if(StringUtils.isNotEmpty(userName)) {
				logger.info("code=======:"+userName);
				member = memMemberService.getMemMemberByUsername(userName);
			}
			//MemMember member = memMemberService.getMemMemberByUsername(userName);
			// 用户名是否存在判断
			if (member == null) {
				msg.setCode(ExceptionEnum.member_not_exist.getIndex());
				msg.setStatus(false);
				msg.setMsg(ExceptionEnum.member_not_exist.getValue() + ":"
						+ userName);
				return msg;
			}
			OrderTable order = orderService.getOrderTableByOrderNo(orderNo,
					userName);
			if (order == null) {
				msg.setCode(ExceptionEnum.order_not_exist.getIndex());
				msg.setStatus(false);
				msg.setMsg(ExceptionEnum.order_not_exist.getValue());
				return msg;
			}
			Product p = null;
			for (OrderTableItem oti : order.getList_ordertable_item()) {
				p = productService.findById(oti.getGoodsNo());
				//BigDecimal pPrice = new BigDecimal(p.getPrice().toString());
				//int ct = pPrice.compareTo(oti.getPrice());
				// 判断商品是否为空
				if (Utils.isBlank(p)) {
					msg.setCode(ExceptionEnum.product_not_exist.getIndex());
					msg.setStatus(false);
					msg.setMsg(ExceptionEnum.product_not_exist.getValue());
					return msg;
				}
				// 判断商品是否下架
				if (p.getWxIsMarketable().equals("0")) {
					msg.setCode(ExceptionEnum.product_undercarriage.getIndex());
					msg.setStatus(false);
					msg.setMsg(ExceptionEnum.product_undercarriage.getValue());
					return msg;
				}
				// 判断库存是否为空
				if (p.getAllocatedStock() <= 0) {
					msg.setCode(ExceptionEnum.product_Low_stock.getIndex());
					msg.setStatus(false);
					msg.setMsg(ExceptionEnum.product_Low_stock.getValue());
					return msg;
				}
				// 判断商品价格是否改动
				/*if (ct != 0) {
					msg.setCode(ExceptionEnum.product_price_different
							.getIndex());
					msg.setStatus(false);
					msg.setMsg(ExceptionEnum.product_price_different.getValue());
					return msg;
				}*/
			}
		} catch (Exception e) {
			msg.setStatus(false);
			msg.setCode(ExceptionEnum.business_logic_exception.getIndex());
			msg.setMsg(ExceptionEnum.business_logic_exception.getValue());
			logger.error("支付提示出错", e);
		}
		logger.info("返回参数:" + JacksonUtil.writeValueAsString(msg));
		logger.info("=======end 手机APP调用paymentCues方法接口====================");
		return msg;
	}
	
	/***
	 * 跳转到去结算页面
	 * @param receiverId 收货地址ID
	 * @param cartId 购物车ID
	 * @param balanceFlag 余额支付标记
	 * @param pid 商品ID
	 * @param order 订单数据
	 * @param request
	 * @param response
	 * @return
	 */
	@RequestMapping("/toSettle")
	@ResponseBody
	public ReturnMsg<OrderTableDto> toSettle(String receiverId, String cartId, String balanceFlag, String[] pid, OrderTable order,
			HttpServletRequest request, HttpServletResponse response) {
		logger.info("order_paymentMethod:"+order.getPaymentMethod());
		ReturnMsg<OrderTableDto> msg = new ReturnMsg<OrderTableDto>();
		OrderTableDto orderDto = new OrderTableDto();
		List<OrderItemDto> orderItemDtos = new ArrayList<OrderItemDto>();
		String userName = (String)UserSession.getSession(WxConstant.wx_user_name);
		try {
			logger.info("=======begin 手机APP调用queryOrder方法接口====================");
			// 非空判断
			if (Utils.isBlank(userName)) {
				msg.setCode(ExceptionEnum.param_not_null.getIndex());
				msg.setStatus(false);
				msg.setMsg(ExceptionEnum.param_not_null.getValue());
				return msg;
			}
			MemMember member = memMemberService.getMemMemberByUsername(userName);
			// 用户名是否存在判断
			if (member == null) {
				msg.setCode(ExceptionEnum.member_not_exist.getIndex());
				msg.setStatus(false);
				msg.setMsg(ExceptionEnum.member_not_exist.getValue());
				return msg;
			}
			order.setUserName(userName);
			msg = orderService.installOrder(order, receiverId, cartId, pid);
			if(!msg.isStatus()){
				return msg;
			}
			BeanUtils.copyProperties(orderDto, order);
			Product p = null;
			BigDecimal productAmount = new BigDecimal(0);
			for (OrderTableItem oti : order.getList_ordertable_item()) {
				OrderItemDto orderItemDto = new OrderItemDto();
				p = productService.findById(oti.getGoodsNo());
			
				BeanUtils.copyProperties(orderItemDto, oti);
				orderItemDto.setAllocatedStock(p.getAllocatedStock());
				orderItemDto.setIsMarketable(p.getWxIsMarketable());
				orderItemDto.setGoodNo(p.getId());
				orderItemDtos.add(orderItemDto);
				// 计算商品总额
				BigDecimal quantity = new BigDecimal(oti.getQuantity()
						.toString());
				BigDecimal price = quantity.multiply(oti.getPrice());
				productAmount = productAmount.add(price);
				// 订单总额
				BigDecimal priceAmount = order.getOrderAmount(productAmount);
				orderDto.setProductAmount(priceAmount);
				orderDto.setList_ordertable_item(orderItemDtos);
			}
			orderDto.setReceiverId(StringUtils.isNotBlank(msg.getMsg()) ? msg.getMsg() : "");
			orderDto.setCouponCount(orderService.getUseCouponCount(cartId, pid, member.getId()));
			msg.setData(orderDto);
		} catch (Exception e) {
			msg.setStatus(false);
			msg.setCode(ExceptionEnum.business_logic_exception.getIndex());
			msg.setMsg(ExceptionEnum.business_logic_exception.getValue());
			logger.error("查询订单详情出错", e);
		}
		logger.info("返回参数:" + JacksonUtil.writeValueAsString(msg));
		logger.info("=======end 手机APP调用queryOrder方法接口====================");
		return msg;
	} 
	
	/***
	 * 获取用户购物券列表
	 * @param cartId 购物车ID
	 * @param pid 商品ID数组
	 * @param sessionId
	 * @return
	 */
	@RequestMapping("/queryCurUserCouponList")
	@ResponseBody
	public ReturnMsg<OrderCouponDto> queryCurUserCouponList(String cartId, String[] pid){
		logger.info("=======begin 微信调用queryCurUserCouponList方法接口====================");
		ReturnMsg<OrderCouponDto> msg = new ReturnMsg<OrderCouponDto>();
		if (Utils.isObjectsBlank(cartId, pid)) {
			msg.setCode(ExceptionEnum.param_not_null.getIndex());
			msg.setStatus(false);
			msg.setMsg(ExceptionEnum.param_not_null.getValue());
			return msg;
		}
		try {
			String userName = (String)UserSession.getSession(WxConstant.wx_user_name);
			if (Utils.isBlank(userName)) {
				msg.setCode(ExceptionEnum.param_not_null.getIndex());
				msg.setStatus(false);
				msg.setMsg(ExceptionEnum.param_not_null.getValue());
				return msg;
			}
			MemMember member = memMemberService
					.getMemMemberByUsername(userName);
			// 用户名是否存在判断
			if (member == null) {
				msg.setCode(ExceptionEnum.member_not_exist.getIndex());
				msg.setStatus(false);
				msg.setMsg(ExceptionEnum.member_not_exist.getValue() + ":"
						+ userName);
				return msg;
			}
			msg = orderService.queryCurUserCouponList(cartId, pid, member.getId());
		} catch (Exception e) {
			e.printStackTrace();
		}
		logger.info("返回参数:" + JacksonUtil.writeValueAsString(msg));
		logger.info("=======end 微信调用queryCurUserCouponList方法接口====================");
		return msg;
	}

}
