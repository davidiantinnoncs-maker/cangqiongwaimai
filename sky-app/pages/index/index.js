import navBar from '../common/Navbar/navbar.vue'
import 
	{ 
		openTable, 
		getTableState, 
		getList, 
		clearOrder, 
		getMoreNorm,
		getDishDetail, 
		getDishList, 
		addDish, 
		delDish, 
		getTableOrderDishList, 
		// 瑞吉外卖相关的接口
		userLogin, 
		getCategoryList, 
		dishListByCategoryId, 
		commonDownload,
		// 加菜
		addShoppingCart,
		// 查询套餐列表的接口
		querySetmeaList,
		// 获取购物车集合
		getShoppingCartList,
		// 新的购物车添加逻辑接口
		newAddShoppingCartAdd,
		// 新的购物车减少接口
		newShoppingCartSub,
		// 清空购物车
		delShoppingCart,
		// 此接口为首页查询套餐详情展示的接口
		querySetmealDishById
	} from '../api/api.js'
import initWebScoket from '../../utils/webscoket'
import {mapState, mapMutations, mapActions} from 'vuex'
import { baseUrl } from '../../utils/env'
export default {
	data () {
		return {
			title: 'Hello',
			// 去结算部分
			openOrderCartList: false,
			// 存放左侧滚动区域菜品分类数组
			typeListData: [],
			dishListData: [],
			// 存放右侧对应菜品每个菜名称的数组
			dishListItems: [],
			dishDetailes: {},
			openDetailPop: false,
			openMoreNormPop: false,
			moreNormDataes: null,
			tableInfo:null,
			moreNormDishdata:null,
			moreNormdata:null,
			// 套餐中查询到的菜品名称
			dishMealData:null,
			openTablePeoPleNumber: 1,
			orderData: 0,
			// 选中左侧菜品的索引
			typeIndex: 0,
			// 控制菜品详情显示
			openTablePop: false,
			// 规格有关的数组
			flavorDataes: [],
			// 加入购物车数量
			orderDishNumber: 0,
			// 菜品金额
			orderDishPrice: 0,
			params: {
				shopId: 'f3deb',
				storeId: '1282344676983062530',
				tableId: '1282346960773238786'
			 },
			 // 添加一个右侧number更新以后重新刷新接口的id --- 这个id来自左侧菜品分类的id
			 rightIdAndType: {},
			 // 微信登录弹层
			 showLoginPop: false,
			 jsCode: '',
			 loginAvatar: '',
			 loginNickname: '',
			 defaultAvatar: '../../static/btn_waiter_sel.png'
		}
	},
	computed: {
		// 购物车信息列表
		orderListDataes: function () {
			return this.orderListData()
			// return this.orderListData().dishList
		},
		loaddingSt: function () {
			return this.lodding()
		},
		orderAndUserInfo: function () {
			let orderData = []
			Array.isArray(this.orderListDataes) && this.orderListDataes.forEach((n,i) => {
				let userData = {}
				userData.nickName = n.name ?? ''
				userData.avatarUrl = n.image ?? ''
				userData.dishList = [n]
				const num = orderData.findIndex(o => o.nickName == userData.nickName)
				if (num != -1) {
					orderData[num].dishList.push(n)
				} else {
					orderData.push(userData)
				}
			})
			return orderData
		},
		ht: function () {
			return uni.getMenuButtonBoundingClientRect().top + uni.getMenuButtonBoundingClientRect().height + 7
		}
	},
	components: { navBar },
	onLoad (options) {
		uni.onNetworkStatusChange(function(res) {
			if (res.isConnected == false) {
				uni.navigateTo({url: '/pages/nonet/index'})
			} 
		})
		this.getData()
	},
	onShow () {
		// 有sessionId免授权
		this.sessionId() && this.init()
	},
	methods: {
		...mapMutations(['setShopInfo', 'initdishListMut', 'setStoreInfo', 'setBaseUserInfo', 'setLodding', 'setSessionId']),
		...mapState(['shopInfo', 'orderListData', 'baseUserInfo', 'lodding', 'sessionId']),
		getData () {
			if (this.sessionId()) {
				return
			}
			uni.login({
				provider: 'weixin',
				success: (loginRes) => {
					if (loginRes.errMsg === 'login:ok') {
						this.jsCode = loginRes.code
						this.showLoginPop = true
					} else {
						uni.showToast({
							title: '微信登录失败，请重试',
							icon: 'none'
						})
					}
				},
				fail: () => {
					uni.showToast({
						title: '微信登录失败，请重试',
						icon: 'none'
					})
				}
			})
		},
		onChooseAvatar (e) {
			this.loginAvatar = e.detail.avatarUrl
		},
		submitLogin () {
			if (!this.loginNickname) {
				uni.showToast({
					title: '请填写昵称',
					icon: 'none'
				})
				return
			}
			const params = {
				code: this.jsCode,
				name: this.loginNickname,
				avatar: this.loginAvatar,
				sex: '0'
			}
			userLogin(params).then(success => {
				if (success.code === 1) {
					const data = success.data || {}
					this.setSessionId(data.token || '')
					this.setBaseUserInfo(JSON.stringify({
						avatarUrl: this.loginAvatar,
						nickName: this.loginNickname,
						gender: '0'
					}))
					this.showLoginPop = false
					this.init()
				} else {
					uni.showToast({
						title: success.msg || '登录失败',
						icon: 'none'
					})
				}
			}).catch(() => {
				uni.showToast({
					title: '登录失败，请重试',
					icon: 'none'
				})
			})
		}, 
		
		async init () {
			// 获取菜品和套餐分类接口
			getCategoryList().then(res => {
				if (res && res.code === 1) {
					this.typeListData = [ ...res.data ]
					if (res.data.length > 0){
						this.getDishListDataes(res.data[this.typeIndex || 0])
					}
				}
			})
			// 调用一次购物车集合---初始化
			this.getTableOrderDishListes()
		},
		// 开桌操作 开桌后初始化websocket结束点餐信息
		// async openTableHandle () {
		// 	openTable({tableId: this.params.tableId, seatNumber: this.openTablePeoPleNumber}).then(res => {
		// 		this.openTablePop = false
		// 		// initWebScoket(this.params)
		// 		this.getTableOrderDishListes()
		// 		this.computOrderInfo()
		// 	}).catch(err => {
		// 	})
		// },
		// 获取菜品列表
		async getDishListDataes (params, index) {
      console.log('=-=-=-=-=-=-=getDishListDataes-=-params=-',params)
			this.rightIdAndType = {}
			this.rightIdAndType = {
				id: params.id,
				type: params.type
			}
			const param = {categoryId: params.id,type: params.type, page: 1, pageSize: 1000,status:1}
			if (params.type === 1) {
				await dishListByCategoryId(param).then(res => {
					if (res && res.code === 1) {
						// 添加一个字段去实时更新加入购物车number数量 ----- newCardNumber
						this.dishListData = res.data && res.data.map((obj) => ({ ...obj, type: 1, newCardNumber: 0 }))
					}
				}).catch(err => {
				})
			} else {
				await querySetmeaList(param).then(success => {
					if (success && success.code === 1) {
						// dishListItems被转换数组---原始this.dishListData
						this.dishListData = success.data && success.data.map((obj) => ({ ...obj, type: 2, newCardNumber: 0 }))
					}
				}).catch(err => {
				})
			}
			this.typeIndex = index
			this.setOrderNum()
		},
		// 重新拼装image
		getNewImage (image) {
			// 后端返回的OSS图片地址已经是完整链接，直接使用
			if (image && (image.indexOf('http://') === 0 || image.indexOf('https://') === 0)) {
				return image
			}
			return `${baseUrl}/common/download?name=${image}`
		},
		// 获取购物车订单列表
		async getTableOrderDishListes () {
			// 调用获取购物车集合接口
			await getShoppingCartList({}).then(res => {
				if (res.code === 1) {
					this.initdishListMut(res.data)
					this.computOrderInfo()
					this.setOrderNum()
				}
			}).catch(err => {
			})
		},
		// 去订单页面
		goOrder () {
			uni.navigateTo({url: '/pages/order/index'})
		},
		// 组装购物车接口参数，兼容菜品、套餐和购物车中的商品
		buildCartParams (item, form) {
			let dishFlavor = ''
			if (this.openMoreNormPop) {
				dishFlavor = this.flavorDataes.join(',')
			} else if (form === '购物车') {
				dishFlavor = item.dishFlavor || ''
			} else {
				dishFlavor = item.dishFlavor || this.flavorDataes.join(',')
			}
			const params = {
				dishFlavor: dishFlavor
			}
			const isDish = item.type === 1 || (form === '购物车' && item.dishId)
			const isSetmeal = item.type === 2 || (form === '购物车' && item.setmealId)
			if (isDish) {
				params.dishId = form === '购物车' ? item.dishId : item.id
			} else if (isSetmeal) {
				params.setmealId = form === '购物车' ? item.setmealId : item.id
			}
			return params
		},
		// 加菜 - 添加菜品
		async addDishAction (item, form) {
			// 规格弹窗打开时必须先选择规格
			if (this.openMoreNormPop && (!this.flavorDataes || this.flavorDataes.length <= 0)) {
				uni.showToast({
					title: '请选择规格',
					icon: 'none'
				})
				return false
			}
			// 普通入口清空上次规格选择，避免口味串到无规格商品上
			if (!this.openMoreNormPop && form !== '购物车') {
				this.flavorDataes = []
			}
			const params = this.buildCartParams(item, form)
			if (!params.dishId && !params.setmealId) {
				uni.showToast({
					title: '商品参数错误',
					icon: 'none'
				})
				return false
			}
			newAddShoppingCartAdd(params).then(res => {
				if (res.code === 1) {
					this.flavorDataes = []
					this.openDetailPop = false
					this.openMoreNormPop = false
					// 调用一次购物车集合---初始化
					this.getTableOrderDishListes()
					// 重新调取刷新右侧具体菜品列表
					this.getDishListDataes(this.rightIdAndType)
				} else {
					uni.showToast({
						title: res.msg || '添加失败',
						icon: 'none'
					})
				}
			}).catch(err => {
				uni.showToast({
					title: (err && err.msg) || '添加失败',
					icon: 'none'
				})
			})
		},
		// 减菜 - 添加菜品
		async redDishAction (item, form) {
			if (!this.openMoreNormPop && form !== '购物车') {
				this.flavorDataes = []
			}
			const params = this.buildCartParams(item, form)
			if (!params.dishId && !params.setmealId) {
				return false
			}
			await newShoppingCartSub(params).then(res => {
				if (res.code === 1) {
					// 调用一次购物车集合---初始化
					this.getTableOrderDishListes()
					// 重新调取刷新右侧具体菜品列表
					this.getDishListDataes(this.rightIdAndType)
				} else {
					uni.showToast({
						title: res.msg || '减少失败',
						icon: 'none'
					})
				}
			}).catch(err => {
				uni.showToast({
					title: (err && err.msg) || '减少失败',
					icon: 'none'
				})
			})
		},
		// 清空购物车
		clearCardOrder () {
			delShoppingCart().then(res => {
				if (res.code === 1) {
					this.openOrderCartList = false
					// 调用一次购物车集合---初始化
					this.getTableOrderDishListes()
					// 重新调取刷新右侧具体菜品列表
					this.getDishListDataes(this.rightIdAndType)
				} else {
					uni.showToast({
						title: res.msg || '清空失败',
						icon: 'none'
					})
				}
			}).catch(err => {
				uni.showToast({
					title: (err && err.msg) || '清空失败',
					icon: 'none'
				})
			})
		},
		// 打开菜品牌详情
		openDetailHandle (item) {
			this.dishDetailes = item
			if (item.type === 2) {
				querySetmealDishById({ id: item.id }).then(res => {
					console.log(res)
					if (res.code === 1) {
						this.openDetailPop = true
						this.dishMealData = res.data
					}
				}).catch(err => {
				})
				// 老接口
				// getDishDetail({setmealId:item.dishId}).then(res => {
				// 	this.openDetailPop = true
				// 	this.dishMealData= res.data
				// }).catch(err => {
				// })
			} else {
				this.openDetailPop = true
			}
		},
		// 多规格数据处理
		moreNormDataesHandle (item) {
      this.flavorDataes.splice(0)
			this.moreNormDishdata = item
			this.openMoreNormPop = true
			this.moreNormdata = item.flavors.map(obj => ({ ...obj, value: JSON.parse(obj.value) }))
      this.moreNormdata.forEach((item)=>{
        if(item.value && item.value.length>0){
          this.flavorDataes.push(item.value[0])
        }
      })
      // this.moreNormdata = item.flavors === null ? [] : item.flavors
			// getMoreNorm({dishId: item.dishId}).then(res => {
			// 	this.openMoreNormPop = true
			// 	this.moreNormdata = res.data
			// }).catch(err => {
			// })
		},
		// 选规格 处理一行只能选择一种 
		checkMoreNormPop (obj, item) {
			let ind
			let findst = obj.some(n => {
				ind = this.flavorDataes.findIndex(o => o == n) 
				return ind != -1
			})
			const num = this.flavorDataes.findIndex(it => it == item)
			if (num == -1 && !findst){
				this.flavorDataes.push(item)
			} else if(findst) {
				this.flavorDataes.splice(ind, 1)
				this.flavorDataes.push(item)
			} else {
				this.flavorDataes.splice(num, 1)
			}
		},
		// 关闭选规格弹窗
		closeMoreNorm (moreNormDishdata) {
			this.flavorDataes.splice(0, this.flavorDataes.length)
			this.openMoreNormPop = false
		},
		// // 设置开桌人数
		// setOpenTableNumber (st) {
		// 	if (st == 'add') {
		// 		this.openTablePeoPleNumber+=1
		// 	} else {
		// 		this.openTablePeoPleNumber =  this.openTablePeoPleNumber > 1 ? this.openTablePeoPleNumber-1 : 1
		// 	}
		// },
		// 订单里和总订单价格计算
		computOrderInfo () {
			let oriData = Array.isArray(this.orderListDataes) ? this.orderListDataes : []
			this.orderDishNumber = this.orderDishPrice = 0
			oriData.forEach((n) => {
				this.orderDishNumber += Number(n.number) || 0
				this.orderDishPrice += (Number(n.number) || 0) * (Number(n.amount) || 0)
			})
		},
		// 处理点餐数量 - 更新菜品已点餐数量
		setOrderNum () {
			let ODate = this.dishListData
			let CData = Array.isArray(this.orderListDataes) ? this.orderListDataes : []
			ODate && ODate.map((obj, index) => {
				obj.dishNumber = 0
				CData.forEach((tg) => {
					if (obj.id === tg.dishId || obj.id === tg.setmealId) {
						obj.dishNumber = tg.number
						obj.dishFlavor = tg.dishFlavor || ''
					}
				})
			})
			if (this.dishListItems.length == 0) {
				this.dishListItems = ODate
			} else {
				this.dishListItems.splice(0, this.dishListItems.length, ...ODate)
			}
		},
	}
}
