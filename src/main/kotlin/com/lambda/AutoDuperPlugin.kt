package com.lambda

import com.lambda.client.plugin.api.Plugin
import com.lambda.modules.AutoDuperModule

internal object AutoDuperPlugin : Plugin() {

    override fun onLoad() {
        // Load any modules, commands, or HUD elements here
        modules.add(AutoDuperModule)
        /*bgJobs.add(BackgroundJob("AutoDuperJob", 10000L) {
            LambdaMod.LOG.info("Hello its me the BackgroundJob of your example plugin.")
        })*/
    }

    override fun onUnload() {
        // Here you can unregister threads etc...
        println("Deez Nuts Shutting Down")
    }
}