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
gradle packageWindowsRelease
```

生成文件位于 `build/release`：

- `jks-viewer-<version>-windows-x64.exe`
- `jks-viewer-<version>-windows-x64.msi`
- `jks-viewer-<version>-windows-portable.zip`

EXE/MSI 安装包由 `jpackage` 生成，在 Windows 上需要可用的 WiX Toolset；GitHub Actions 会自动安装。

portable 版本是自包含应用目录，不安装服务、不写开始菜单、不往系统目录安装文件。启用 portable 运行时后，Java 临时目录和偏好目录会落在程序目录的 `.portable-data` 下。

## GitHub 自动构建

推送 `v` 开头的 tag 会触发 `.github/workflows/windows-release.yml`。

- `v0.0.1`：正式版，构建 EXE、MSI、portable 后发布到 GitHub Release
- `v0.0.7-alpha`：测试版，构建后上传到 GitHub Actions artifacts
- 版本号不足三段会自动补齐，例如 `v1.2` 构建为 `1.2.0`
- 版本号超过三段会自动截断，例如 `v1.2.3.4` 构建为 `1.2.3`
