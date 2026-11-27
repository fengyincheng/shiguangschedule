# 广东海洋大学版本 2.1.1-gdou.1

对应需求：[考试查询 #1](https://github.com/fengyincheng/shiguangschedule/issues/1)、[快捷刷新课表 #2](https://github.com/fengyincheng/shiguangschedule/issues/2)。

- 主导航新增「考试」：学年学期选择、考试安排、考试状态、详情与成绩查询。
- 考试查询校验分页总数；成绩支持展示学分、绩点和同课多条成绩。
- 课表左上角新增「刷新」：选择学期、获取最新排课、确认覆盖当前课表；保留作息和学期配置。
- 排课完整解析成功后使用数据库事务覆盖。空课表、无法识别的周次、实践课特殊结构会保留原课表并给出提示。
- 学校原始页面登录，考试/成绩/课表共享 Cookie。教务会话由 Android Keystore 加密保存在本机，重启后复用；学校判定失效时重新登录。

## 安装与使用

优先下载 universal APK，也可按手机架构选择 arm64-v8a 或 armeabi-v7a。Android 8.0 及以上。

安装包标识为 `com.xingheyuzhuan.shiguangschedule.gdou`，可以与原版并存。首次使用可从原版导出课表 JSON，再导入此版本，以保留原来的学期与作息设置。

进入「考试」点击「登录」，完成广东海洋大学统一身份认证后点「完成」，再刷新。后续直接点击刷新即可。考试安排和成绩以学校发布内容为准。

## 签名与构建

GitHub Actions 工作流 `GDOU Android APK` 执行解析/分页单元测试并构建 APK。本机使用专属 fork 签名完成发布。签名材料位于构建机忽略目录 `.signing/`，后续版本应使用同一密钥。

本次网页原型已实测取得三条考试数据；Android 真机登录与显示需要安装后验证。
