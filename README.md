# JKS Viewer

一个面向 Windows 的图形化 Android JKS 查看与维护工具。

## 功能

- 打开或新建 `.jks` / `.keystore` 文件
- 查看 alias 数量、条目类型、证书主体、签发者、有效期、序列号等信息
- 对每个 alias 单独验证条目密码是否正确
- 生成新的 Android 签名 alias
- 删除 alias，并保存回原 JKS 文件

## 运行

```powershell
gradle run
```

## 测试

```powershell
gradle test
```
