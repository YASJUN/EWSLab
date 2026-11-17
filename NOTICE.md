# 来源与许可证

本项目为独立的 EWSLab 开发项目，计划由使用者在自行创建的新仓库中发布；未配置Git远程，也不向原作者仓库提交或推送。

本地Git包含 `private-yusuke/EWSParserApp` 的15个原始提交，基线为 `e3473c48feb8cd6e94665232fb8f5c448d0ff2a5`，锚点 `upstream-reference-e3473c4`。原源码作者 public_yusuke 的 MIT 许可证及署名保留在 LICENSE。原始历史不代表原作者为此重写版背书。

协议数据依据日本总务省告示第405号、無線設備規則第9条の3及無線局運用規則第138条の2。对照了以下公开实现，未将其他项目不一致的波形当作规范：

- https://github.com/private-yusuke/EWSParserApp
- https://github.com/azzbm/JP-EAS-Generator
- https://github.com/ingen084/EwsDemodulator
- https://github.com/radiolab81/EWBS_JP
- https://github.com/nbtk/EWSDemodulator
- https://github.com/Yanai-Taketo/Emergency-Warning-System

新核心使用独立实现，不打包第三方录音、README图片或其源码副本。生成器为本会话核对版本的本地副本；历史JSON为情境节选，未经确认为完整JMA原始档案。

官方依据：https://www.tele.soumu.go.jp/horei/law_honbun/72103000.html

新增Java核心、Android界面、测试及脚本按本仓库MIT许可提供；不将其他参考实现的许可证或作者署名移植为新增代码署名。上游README截图和2021年CI自动发布逻辑未沿用到当前工作树，仍可在原始历史中查阅。
