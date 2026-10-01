import * as echarts from 'echarts'
import { onBeforeUnmount, ref } from 'vue'

/**
 * ECharts 轻封装
 *
 * 每个报表页都要「初始化 → 换数据 → 窗口缩放自适应 → 销毁」这套流程，
 * 抽出来避免四个报表页各写一遍 resize 监听（漏掉 dispose 就是内存泄漏）。
 */
export function useChart() {
  const chartRef = ref(null)
  let chart = null
  let resizeHandler = null

  function ensure() {
    if (chart || !chartRef.value) return chart
    chart = echarts.init(chartRef.value)
    resizeHandler = () => chart && chart.resize()
    window.addEventListener('resize', resizeHandler)
    return chart
  }

  /** 换数据；option 传 null 表示清空 */
  function render(option) {
    const instance = ensure()
    if (!instance) return
    if (!option) {
      instance.clear()
      return
    }
    instance.setOption(option, true)
  }

  /** 容器从隐藏变可见（比如切 Tab）后需要手动 resize */
  function resize() {
    chart && chart.resize()
  }

  onBeforeUnmount(() => {
    if (resizeHandler) window.removeEventListener('resize', resizeHandler)
    if (chart) {
      chart.dispose()
      chart = null
    }
  })

  return { chartRef, render, resize }
}

/** 折线/柱状图通用骨架 */
export function lineOption({ xData = [], series = [], yName = '' } = {}) {
  return {
    tooltip: { trigger: 'axis' },
    legend: { top: 0, right: 10, icon: 'roundRect' },
    grid: { left: 12, right: 18, top: 42, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: xData,
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      axisLabel: { color: '#909399' }
    },
    yAxis: {
      type: 'value',
      name: yName,
      nameTextStyle: { color: '#909399' },
      splitLine: { lineStyle: { color: '#f0f2f5' } },
      axisLabel: { color: '#909399' }
    },
    series
  }
}

/** 柱状图（boundaryGap 打开） */
export function barOption({ xData = [], series = [], yName = '' } = {}) {
  const opt = lineOption({ xData, series, yName })
  opt.xAxis.boundaryGap = true
  opt.xAxis.axisLabel = { color: '#909399', interval: 0, rotate: xData.length > 8 ? 30 : 0 }
  return opt
}

/** 饼图骨架 */
export function pieOption({ data = [], name = '占比' } = {}) {
  return {
    tooltip: { trigger: 'item', formatter: '{b}<br/>{c}（{d}%）' },
    legend: { bottom: 0, icon: 'circle' },
    series: [
      {
        name,
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{b}\n{d}%', color: '#606266', fontSize: 12 },
        data
      }
    ]
  }
}

/** 一套配色，四个报表页统一观感 */
export const PALETTE = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#9b59b6', '#1abc9c']
