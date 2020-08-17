package com.video.edit.activity

import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity

import com.video.edit.demo.R
import com.video.edit.view.Camera2BasicFragment

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraEffectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_effect_activity_layout)

        if (null == savedInstanceState) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.container, Camera2BasicFragment.newInstance())
                    .commit()
        }
    }


}