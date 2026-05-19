# JKS Viewer

一个面向 Windows 的图形化 Android JKS 查看与维护工具。

## 功能

- 打开或新建 `.jks` / `.keystore` 文件
- 查看 alias 数量、条目类型、证书主体、签发者、有效期、序列号等信息
- 对每个 alias 单独验证条目密码是否正确
- 生成新的 Android 签名 alias（RSA 私钥 + 自签名 X.509 证书）
- 删除 alias，并保存回原 JKS 文件

## 运行

```powershell
gradle run
```

## 测试

```powershell
gradle test
```

## 打包 Windows 可运行版本

```powershell
gradle packageWindows
```

生成文件位于 `build/distributions/jks-viewer-1.0.0-windows.zip`。解压后运行 `bin/jks-viewer.bat`。

## 生成 Windows 应用目录

```powershell
gradle packageWindowsApp
```

生成目录位于 `build/jpackage/JKS Viewer`。运行其中的 `JKS Viewer.exe`。
