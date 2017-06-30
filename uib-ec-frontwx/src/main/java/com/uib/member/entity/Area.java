/**
 * Copyright &copy; 2012-2014 <a href="https://github.com/thinkgem/easypay_ec">easypay_ec</a> All rights reserved.
 */
package com.uib.member.entity;

import java.util.List;

import org.hibernate.validator.constraints.Length;

import com.google.common.collect.Lists;

/**
 * 区域Entity
 */
public class Area  {

	private String id;	//id
	private String parentId;	// 父级编号
	private String parentIds; // 所有父级编号
	private String code; 	// 区域编码
	private String name; 	// 区域名称
	private Integer sort;		// 排序
	private String type; 	// 区域类型（1：国家；2：省份、直辖市；3：地市；4：区县）
	
	private List<MemReceiver> memReceiverList = Lists.newArrayList();
	
	public Area(){
		super();
		this.sort = 30;
	}


	@Length(min=1, max=2000)
	public String getParentIds() {
		return parentIds;
	}

	public void setParentIds(String parentIds) {
		this.parentIds = parentIds;
	}
	
	@Length(min=1, max=100)
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Integer getSort() {
		return sort;
	}

	public void setSort(Integer sort) {
		this.sort = sort;
	}

	@Length(min=1, max=1)
	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	@Length(min=0, max=100)
	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}


	
	@Override
	public String toString() {
		return name;
	}



	public String getId() {
		return id;
	}


	public void setId(String id) {
		this.id = id;
	}


	public String getParentId() {
		return parentId;
	}


	public void setParentId(String parentId) {
		this.parentId = parentId;
	}


	public List<MemReceiver> getMemReceiverList() {
		return memReceiverList;
	}


	public void setMemReceiverList(List<MemReceiver> memReceiverList) {
		this.memReceiverList = memReceiverList;
	}
}