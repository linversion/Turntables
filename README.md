# Turntables
一个通过录屏的方式来检测app流畅度的工具。

### 背景
看到闲鱼技术写的一篇文章[他把闲鱼APP长列表流畅度翻了倍（良心教程）](https://juejin.cn/post/6888887439922987022)，里面说到目前安卓端的流畅度检测工具存在着不支持多平台、无法检测竞品、指标选择和用户体验一致性、流畅度数据影响因素多等问题，因此提出使用录屏的方式来进行流畅度检测的想法，方案如下：

![方案](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/8678f96136fd4508a4df6b3c2114e30b~tplv-k3u1fbpfcp-zoom-1.image)

定义了流畅度指标为平均FPS值和1s大卡顿次数。  
平均 FPS：定义一次检测的平均帧率。反应画面平均停留时长。  
1s 大卡顿次数：平均 1s 内出现占用 3 帧及以上的画面次数，反应画面停留时长跳变。  

为排除人为滑动操作对流畅度数值的干扰，使用adb命令操作手机。
```shell
点击：adb shell input tap $x $y
滑动：adb shell input swipe $x1 $y1 $x2 $y2 $duration
```

### 结果演示
![res](screenshots/result.jpg)