package com.uib.product.service;

import java.util.List;
import java.util.Map;

import com.uib.product.entity.MemberFavoriteProduct;

public interface MemberFavoriteProductService {
	/**
	 * 查询收藏夹所有信息
	 * @param memberId
	 * @return
	 * @throws Exception
	 */
	List<Map<String,Object>> getMemberFavoriteProductsByMemberId(String memberId) throws Exception;
	/**
	 * 根据会员编号和商品编号查询收藏夹所有信息
	 * @param memberId
	 * @return
	 * @throws Exception
	 */
	MemberFavoriteProduct getMemberFavoriteProducts(String memberId,String productId) throws Exception;
	/**
	 * 保存商品信息到收藏夹
	 * @param memberFavoriteProduct
	 * @throws Exception
	 */
	void saveFavorite(MemberFavoriteProduct memberFavoriteProduct) throws Exception;
	
	/**
	 * 根据id删除收藏夹内容
	 * @param id
	 * @throws Exception
	 */
	void deleteFavorite(String id) throws Exception;
	
	/***
	 * 根据用户ID 获取收藏商品数量
	 * @param memberId
	 * @return
	 * @throws Exception
	 */
	Integer getFavoriteCount(String memberId) throws Exception;
	
}
