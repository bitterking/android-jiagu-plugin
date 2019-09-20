package com.s.android.plugin.jiagu

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.*
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class JiaGuTask extends DefaultTask {

    static final String NAME = "sJiaGu"

    private String commandJiaGu
    private String commandExt = ""
    private JiaGuPluginExtension jiaGuPluginExtension
    private static boolean debug = false
    private OkHttpClient okHttpClient = new OkHttpClient()

    JiaGuTask() {
        group = "JiaGu"
        description = "360 jiagu plugin"
    }

    /**
     * 加固登录
     */
    private String login() {
        return exec(commandJiaGu + " -login ${jiaGuPluginExtension.username} ${jiaGuPluginExtension.password}")
    }

    /**
     * 导入签名信息
     */
    private String importSign() {
        try {
            if (jiaGuPluginExtension.storeFile == null || !jiaGuPluginExtension.storeFile.exists()) {
                jiaGuPluginExtension.storeFile = project.android.buildTypes.release.signingConfig.storeFile
                jiaGuPluginExtension.storePassword = project.android.buildTypes.release.signingConfig.storePassword
                jiaGuPluginExtension.keyAlias = project.android.buildTypes.release.signingConfig.keyAlias
                jiaGuPluginExtension.keyPassword = project.android.buildTypes.release.signingConfig.keyPassword
            }
        } catch (Exception ex) {
            ex.printStackTrace()
        }
        if (jiaGuPluginExtension.storeFile != null && jiaGuPluginExtension.storeFile.exists()) {
            commandExt += " -autosign "
            return exec(commandJiaGu + " -importsign ${jiaGuPluginExtension.storeFile.getAbsolutePath()}" +
                    " ${jiaGuPluginExtension.storePassword}  ${jiaGuPluginExtension.keyAlias}  ${jiaGuPluginExtension.keyPassword}")
        }
        return "未导入签名信息"
    }

    /**
     * 导入渠道信息
     */
    private String importMulPkg() {
        if (jiaGuPluginExtension.channelFile != null && jiaGuPluginExtension.channelFile.exists()) {
            commandExt += " -automulpkg "
            return exec(commandJiaGu + " -importmulpkg ${jiaGuPluginExtension.channelFile}")
        }
        return "未导入渠道信息"
    }

    /**
     * 配置加固服务
     */
    private String setConfig() {
        if (jiaGuPluginExtension.config == null || jiaGuPluginExtension.config.isEmpty()) {
            // 选择崩溃日志服务、支持x86架构设备、选择数据分析服务
            jiaGuPluginExtension.config = "-crashlog -x86 -analyse"
        }
        // 配置加固服务
        return exec(commandJiaGu + " -config ${jiaGuPluginExtension.config}")
    }

    /**
     * 加固
     */
    private String jiaguStart() {
        if (jiaGuPluginExtension.inputFilePath == null || jiaGuPluginExtension.inputFilePath.isEmpty()) {
            String outputFilePath = ""
            String taskName = getName()
            try {
                project.android.applicationVariants.all { variant ->
                    variant.outputs.all { output ->
                        if (taskName.contains(variant.name.capitalize())) {
                            outputFilePath = output.outputFile.getAbsolutePath()
                        }
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace()
            }
            jiaGuPluginExtension.inputFilePath = outputFilePath
        }
        if (jiaGuPluginExtension.outputFileDir == null || jiaGuPluginExtension.outputFileDir.isEmpty()) {
            jiaGuPluginExtension.outputFileDir = "${project.buildDir.getAbsolutePath()}\\jiagu"
        }
        def outputFile = new File(jiaGuPluginExtension.outputFileDir)
        if (!outputFile.exists()) {
            outputFile.mkdirs()
        }
        // 应用加固
        String cmd = commandJiaGu + " -jiagu ${jiaGuPluginExtension.inputFilePath} ${jiaGuPluginExtension.outputFileDir}"
        return exec(cmd + commandExt)
    }

    /**
     * 插件开始
     */
    @TaskAction
    void start() {
        jiaGuPluginExtension = project.extensions.findByName(JiaGuPlugin.EXTENSION_NAME) as JiaGuPluginExtension
        debug = jiaGuPluginExtension.debug
        if (!jiaGuPluginExtension.enable) {
            Logger.debug("enable: false")
            return
        }
        if (jiaGuPluginExtension.jiaguEnable) {
            startJiagu()
        }
        if (jiaGuPluginExtension.firEnable) {
            firUpload()
        }
    }

    /**
     * 开始加固
     */
    void startJiagu() {
        if (jiaGuPluginExtension.jiaGuDir == null || jiaGuPluginExtension.jiaGuDir.isEmpty()) {
            throw new NullPointerException("jiaGuDir 必填")
        }
        if (jiaGuPluginExtension.username == null || jiaGuPluginExtension.username.isEmpty()) {
            throw new NullPointerException("username 必填")
        }
        if (jiaGuPluginExtension.password == null || jiaGuPluginExtension.password.isEmpty()) {
            throw new NullPointerException("password 必填")
        }
        def jiaguDirFile = new File(jiaGuPluginExtension.jiaGuDir)
        if (jiaguDirFile == null || !jiaguDirFile.exists()) {
            throw new NullPointerException("jiaGuDir 不存在")
        }
        def jiaguJarFile = new File("${jiaGuPluginExtension.jiaGuDir}\\jiagu.jar")
        if (jiaguJarFile == null || !jiaguJarFile.exists()) {
            throw new NullPointerException("jiagu.jar 不存在")
        }
        commandJiaGu = "${jiaGuPluginExtension.jiaGuDir}\\java\\bin\\java -jar ${jiaGuPluginExtension.jiaGuDir}\\jiagu.jar "
        Logger.debug("-----start-----")
        // 登录
        String result = login()
        if (result.contains("success")) {
            Logger.debug("login success")
            // 导入签名keystore信息
            result = importSign()
            if (result.contains("succeed")) {
                result = "导入签名 succeed"
            }
            Logger.debug(result)
            // 导入渠道信息
            Logger.debug(importMulPkg())
            // 配置加固服务
            result = setConfig()
            if (result.contains("config saving succeed.")) {
                def indexOf = result.indexOf("已选增强服务")
                if (indexOf > -1) {
                    result = result.substring(indexOf).trim()
                } else {
                    result = "已选增强服务：${jiaGuPluginExtension.config}"
                }
            }
            Logger.debug(result)
            Logger.debug("加固中........")
            // 加固
            result = jiaguStart()
            if (result.contains("任务完成_已签名")) {
                result = "任务完成_已签名"
            }
            Logger.debug(result)
            Logger.debug("输出目录：${jiaGuPluginExtension.outputFileDir}")
        } else {
            Logger.debug(result)
            throw new RuntimeException("登录失败")
        }
        Logger.debug("-----end-----")
    }

    /**
     * firUpload
     */
    void firUpload() {
        String firApiToken = jiaGuPluginExtension.firApiToken
        if (firApiToken == null || firApiToken.isEmpty()) {
            throw new NullPointerException("firApiToken can not be null.")
        }
        if (jiaGuPluginExtension.appName == null || jiaGuPluginExtension.appName.isEmpty()) {
            throw new NullPointerException("App Name can not be null.")
        }
        String firBundleId = jiaGuPluginExtension.firBundleId
        if (firBundleId == null || firBundleId.isEmpty()) {
            firBundleId = project.android.defaultConfig.applicationId
        }
        if (firBundleId == null || firBundleId.isEmpty()) {
            throw new NullPointerException("firBundleId can not be null.")
        }
        Logger.debug("obtain upload credentials...")
        FormBody.Builder formBodyBuild = new FormBody.Builder()
        formBodyBuild.add("type", "android")
        formBodyBuild.add("bundle_id", firBundleId)
        formBodyBuild.add("api_token", firApiToken)
        Request.Builder builder = new Request.Builder()
                .url("http://api.fir.im/apps")
                .post(formBodyBuild.build())
        Response response = okHttpClient.newCall(builder.build()).execute()
        if (response != null && response.code() == 201) {
            def string = response.body().string()
            Logger.debug(string)
            JsonObject jsonObject = new JsonParser().parse(string).asJsonObject.getAsJsonObject("cert")
            def binaryObject = jsonObject.getAsJsonObject("binary")
            firUploadApk(binaryObject.get("upload_url").asString, binaryObject.get("key").asString,
                    binaryObject.get("token").asString, jsonObject.get("prefix").asString)
        } else {
            Logger.debug("Unable to obtain upload credentials. $response")
        }
    }

    /**
     * 上传apk
     */
    void firUploadApk(String url, String key, String token, String prefix) {
        String taskName = getName()
        String versionCode = null
        String versionName = null
        File uploadFile = null
        try {
            project.android.applicationVariants.all { variant ->
                variant.outputs.all { output ->
                    if (taskName.contains(variant.name.capitalize())) {
                        versionCode = variant.versionCode
                        versionName = variant.versionName
                        uploadFile = output.outputFile
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace()
        }
        if (uploadFile == null || !uploadFile.exists()) {
            Logger.debug("not apk file.")
            return
        }
        if (jiaGuPluginExtension.jiaguEnable) {
            String name = uploadFile.name.substring(0, uploadFile.name.lastIndexOf(".")) +
                    "_" + versionName.replace(".", "") + "_jiagu_sign.apk"
            File file = new File(jiaGuPluginExtension.outputFileDir + "\\" + name)
            if (file.exists()) {
                uploadFile = file
            }
        }
        Logger.debug("upload apk. path: ${uploadFile.path}  " +
                "\nurl:$url" +
                "\nkey:$key" +
                "\ntoken:$token" +
                "\n" + "${prefix}name:" + jiaGuPluginExtension.appName +
                "\n" + "${prefix}version:" + versionCode +
                "\n" + "${prefix}build:" + versionName +
                "\n" + "${prefix}changelog:" + jiaGuPluginExtension.firChangeLog
        )
        MultipartBody.Builder bodybuilder = new MultipartBody.Builder()
        bodybuilder.setType(MultipartBody.FORM)
        bodybuilder.addFormDataPart("key", key)
        bodybuilder.addFormDataPart("token", token)
        bodybuilder.addFormDataPart("file", uploadFile.getName(), RequestBody.create(null, uploadFile))
        bodybuilder.addFormDataPart("${prefix}name", jiaGuPluginExtension.appName)
        bodybuilder.addFormDataPart("${prefix}version", versionCode)
        bodybuilder.addFormDataPart("${prefix}build", versionName)
        bodybuilder.addFormDataPart("${prefix}changelog", jiaGuPluginExtension.firChangeLog)
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(bodybuilder.build())
        Response response = okHttpClient.newCall(builder.build()).execute()
        if (response != null && response.body() != null && response.code() == 200) {
            def string = response.body().string()
            Logger.debug(string)
            boolean isCompleted = new JsonParser().parse(string).asJsonObject.get("is_completed").asBoolean
            Logger.debug("is_completed : $isCompleted")
        } else {
            Logger.debug("upload apk failure. $response")
        }
    }

    /**
     * 执行命令行
     *
     * @param command 命令
     * @return 结果
     */
    static String exec(String command) throws InterruptedException {
        String returnString = ""
        Runtime runTime = Runtime.getRuntime()
        if (runTime == null) {
            Logger.debug("Create runtime failure!")
        }
        try {
            if (debug) {
                Logger.debug(command)
            }
            Process pro = runTime.exec(command)
            BufferedReader input = new BufferedReader(new InputStreamReader(pro.getInputStream(), "GBK"))
            String line
            while ((line = input.readLine()) != null) {
                returnString = returnString + line + "\n"
            }
            input.close()
            pro.destroy()
        } catch (IOException ex) {
            ex.printStackTrace()
        }
        if (debug) {
            Logger.debug(returnString)
        }
        return returnString
    }
}