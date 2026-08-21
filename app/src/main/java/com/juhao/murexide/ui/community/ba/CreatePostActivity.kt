package com.juhao.murexide.ui.community.ba

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.ui.community.detail.PostDetailActivity
import com.juhao.murexide.ui.theme.MurexideTheme

class CreatePostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val postId = intent.getIntExtra(EXTRA_POST_ID, -1)
        val isEditMode = postId != -1

        val baId = intent.getIntExtra(EXTRA_BA_ID, -1)
        val baName = intent.getStringExtra(EXTRA_BA_NAME) ?: ""
        if (!isEditMode && baId <= 0) {
            Toast.makeText(this, "无效的分区ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val content = intent.getStringExtra(EXTRA_CONTENT) ?: ""
        val contentType = intent.getIntExtra(EXTRA_CONTENT_TYPE, 1)

        val viewModel: CreatePostViewModel by viewModels {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CreatePostViewModel(
                        accountStorage = AccountStorage.getInstance(this@CreatePostActivity),
                        baId = baId
                    ) as T
                }
            }
        }

        setContent {
            MurexideTheme {
                CreatePostScreen(
                    baName = baName,
                    onClose = { finish() },
                    onPublished = {
                        Toast.makeText(this, "发布成功", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    viewModel = viewModel,
                    postId = postId.takeIf { isEditMode },
                    title = title,
                    content = content,
                    contentType = contentType
                )
            }
        }
    }

    companion object {
        const val EXTRA_BA_ID = "ba_id"
        const val EXTRA_BA_NAME = "ba_name"
        const val EXTRA_POST_ID = "post_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CONTENT = "content"
        const val EXTRA_CONTENT_TYPE = "content_type"

        fun startCreate(context: Context, baId: Int, baName: String) {
            val intent = Intent(context, CreatePostActivity::class.java).apply {
                putExtra(EXTRA_BA_ID, baId)
                putExtra(EXTRA_BA_NAME, baName)
                if (context !is ComponentActivity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }

        fun startEdit(context: Context, postId: Int, title: String, content: String, contentType: Int) {
            val intent = Intent(context, CreatePostActivity::class.java).apply {
                putExtra(EXTRA_POST_ID, postId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_CONTENT, content)
                putExtra(EXTRA_CONTENT_TYPE, contentType)
                if (context !is ComponentActivity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }
    }
}
