GitHub Actions 云端打包说明

1. 解压 EnterpriseCustomerManager_V1_3_2.zip。
2. 上传 EnterpriseCustomerManager_V1_3_2 文件夹内的全部内容到 GitHub 仓库根目录：
   app
   .github
   build.gradle
   settings.gradle
   README_功能说明.txt
   README_云端打包说明.txt
3. 进入仓库顶部 Actions。
4. 选择 Build Android APK。
5. 点击 Run workflow。
6. 等待绿色对勾。
7. 下载 Artifacts 中的 enterprise-customer-manager-debug-apk。
8. 解压后得到 app-debug.apk。
9. 手机安装时请选择覆盖安装，不要先卸载旧版。

如果 GitHub 没有出现 Build Android APK，说明 .github/workflows/build-apk.yml 没有上传成功，需要手动补该文件。
