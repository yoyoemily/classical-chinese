#!/usr/bin/env node
/**
 * classical-chinese 服务端部署脚本
 *
 * 一键完成：
 * 1. SCP 上传 jar 包到服务器
 * 2. SSH 执行重启脚本
 *
 * 使用方法：
 *   node deploy.js
 *
 * 需要 Node.js 18+，支持 ES Module
 */

import { execSync } from 'child_process'
import { existsSync } from 'fs'
import { resolve } from 'path'
import { homedir } from 'os'

// ==================== 配置（按需修改）====================
const CONFIG = {
    // 本地 jar 包路径（相对于脚本位置或绝对路径）
    LOCAL_JAR: resolve(process.cwd(), 'target/classical-chinese.jar'),

    // SSH 配置
    SSH_KEY: resolve(homedir(), '.ssh/aliyun/yinque-key.pem'),
    SSH_PORT: '31749',
    SERVER: 'service@47.97.254.112',

    // 远程目录
    REMOTE_DIR: '/home/service',
    REMOTE_JAR_NAME: 'classical-chinese.jar',
    RESTART_SCRIPT: '.classical-chinese.sh restart'
}
// =====================================================

// 检查本地文件
function checkLocalFile() {
    console.log('🔍 检查本地 jar 包...')

    if (!existsSync(CONFIG.LOCAL_JAR)) {
        console.error(`❌ 文件不存在: ${CONFIG.LOCAL_JAR}`)
        console.error('   请先执行 Maven 构建: mvn clean package -DskipTests')
        process.exit(1)
    }

    const stats = execSync(`ls -lh "${CONFIG.LOCAL_JAR}"`, { encoding: 'utf8' }).trim()
    console.log(`✅ ${stats}`)
}

// 上传 jar 包
function uploadJar() {
    console.log('\n📤 上传 jar 包到服务器...')
    console.log(`   目标: ${CONFIG.SERVER}:${CONFIG.REMOTE_DIR}/`)

    try {
        execSync(
            `scp -P ${CONFIG.SSH_PORT} -i "${CONFIG.SSH_KEY}" "${CONFIG.LOCAL_JAR}" ${CONFIG.SERVER}:${CONFIG.REMOTE_DIR}/${CONFIG.REMOTE_JAR_NAME}`,
            { stdio: 'inherit' }
        )
        console.log('✅ 上传成功')
    } catch (err) {
        console.error('\n❌ 上传失败:', err.message)
        process.exit(1)
    }
}

// 执行远程重启
function remoteRestart() {
    console.log('\n🚀 执行远程重启...')

    try {
        execSync(
            `ssh -p ${CONFIG.SSH_PORT} -i "${CONFIG.SSH_KEY}" ${CONFIG.SERVER} 'cd ${CONFIG.REMOTE_DIR} && ${CONFIG.RESTART_SCRIPT}'`,
            { stdio: 'inherit' }
        )
        console.log('\n✅ 部署完成！')
    } catch (err) {
        console.error('\n❌ 重启失败:', err.message)
        process.exit(1)
    }
}

// 查看服务状态
function checkStatus() {
    console.log('\n📊 检查服务状态...')

    try {
        const result = execSync(
            `ssh -p ${CONFIG.SSH_PORT} -i "${CONFIG.SSH_KEY}" ${CONFIG.SERVER} 'cd ${CONFIG.REMOTE_DIR} && ./classical-chinese.sh status 2>/dev/null || ps aux | grep classical-chinese | grep -v grep'`,
            { encoding: 'utf8' }
        )
        console.log(result || '服务运行中')
    } catch {
        console.log('⚠️  无法获取状态，请手动检查')
    }
}

// 主流程
async function deploy() {
    console.log('🎯 classical-chinese 服务端部署')
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n')

    // 1. 检查本地文件
    checkLocalFile()

    // 2. 上传
    uploadJar()

    // 3. 重启
    remoteRestart()

    // 4. 查看状态（可选）
    checkStatus()

    console.log('\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━')
}

// 参数处理
const args = process.argv.slice(2)

if (args.includes('--help') || args.includes('-h')) {
    console.log(`
classical-chinese 服务端部署脚本

用法:
  node deploy.js        完整部署流程
  node deploy.js --status   仅查看服务状态
  node deploy.js --help     显示帮助

流程:
  1. 检查本地 jar 包是否存在
  2. SCP 上传到服务器 /home/service/
  3. SSH 执行 ./classical-chinese.sh restart
  4. 显示服务状态
`)
    process.exit(0)
}

if (args.includes('--status') || args.includes('-s')) {
    checkStatus()
    process.exit(0)
}

// 执行部署
deploy().catch(err => {
    console.error('❌ 部署失败:', err.message)
    process.exit(1)
})
