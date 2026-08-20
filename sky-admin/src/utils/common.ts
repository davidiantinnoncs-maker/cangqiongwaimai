export const checkProcessEnv =() => {
  return process.env.VUE_APP_DELETE_PERMISSIONS==='true'
}

// OSS图片默认是attachment下载，统一走后端common/download转成inline展示
export const getImageUrl = (image: string) => {
  if (!image) {
    return ''
  }
  const origin = (process.env.VUE_APP_URL || '').replace(/\/+$/, '').replace(/\/admin$/, '')
  return `${origin}/common/download?name=${encodeURIComponent(image)}`
}

export const debounce=(fn, time)=> {
  time = time || 200
  // 定时器
  let timer = null
  return function(...args) {
    var _this = this
    if (timer) {
      clearTimeout(timer)
    }
    timer = setTimeout(function() {
      timer = null
      fn.apply(_this, args)
    }, time)
  }
  
};
//节流
export const throttle = (fn, time)=> {
  let timer = null
  time = time || 1000
  return function(...args) {
    if (timer) {
      return
    }
    const _this = this
    timer = setTimeout(() => {
      timer = null
    }, time)
    fn.apply(_this, args)
  }
}
// 判断正、负
export const strIncrease = (str)=>{
  if(str.slice(0,1) ==='-'){
    return true
    }
}
