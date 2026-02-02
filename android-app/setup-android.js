import { copyFileSync, readFileSync, writeFileSync, existsSync } from 'fs'
import { dirname, join } from 'path'
import { fileURLToPath } from 'url'

const __dirname = dirname(fileURLToPath(import.meta.url))

// Copy the native plugin
const pluginSrc = join(__dirname, 'android-plugin', 'TimerNotificationPlugin.java')
const pluginDest = join(__dirname, 'android', 'app', 'src', 'main', 'java', 'org', 'yatt', 'app', 'TimerNotificationPlugin.java')

if (existsSync(pluginSrc)) {
  copyFileSync(pluginSrc, pluginDest)
  console.log('Copied TimerNotificationPlugin.java')
}

// Register the plugin in MainActivity
const mainActivityPath = join(__dirname, 'android', 'app', 'src', 'main', 'java', 'org', 'yatt', 'app', 'MainActivity.java')

if (existsSync(mainActivityPath)) {
  let mainActivity = readFileSync(mainActivityPath, 'utf8')
  
  // Check if already registered
  if (!mainActivity.includes('TimerNotificationPlugin')) {
    // Add import
    mainActivity = mainActivity.replace(
      'import com.getcapacitor.BridgeActivity;',
      `import com.getcapacitor.BridgeActivity;
import org.yatt.app.TimerNotificationPlugin;`
    )
    
    // Register plugin in onCreate or add onCreate if not exists
    if (mainActivity.includes('onCreate')) {
      mainActivity = mainActivity.replace(
        'super.onCreate(savedInstanceState);',
        `super.onCreate(savedInstanceState);
        registerPlugin(TimerNotificationPlugin.class);`
      )
    } else {
      mainActivity = mainActivity.replace(
        'public class MainActivity extends BridgeActivity {',
        `public class MainActivity extends BridgeActivity {
    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerPlugin(TimerNotificationPlugin.class);
    }`
      )
    }
    
    writeFileSync(mainActivityPath, mainActivity)
    console.log('Registered TimerNotificationPlugin in MainActivity')
  }
}

// Add notification permission to AndroidManifest
const manifestPath = join(__dirname, 'android', 'app', 'src', 'main', 'AndroidManifest.xml')

if (existsSync(manifestPath)) {
  let manifest = readFileSync(manifestPath, 'utf8')
  
  if (!manifest.includes('POST_NOTIFICATIONS')) {
    manifest = manifest.replace(
      '<uses-permission android:name="android.permission.INTERNET" />',
      `<uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`
    )
    writeFileSync(manifestPath, manifest)
    console.log('Added POST_NOTIFICATIONS permission')
  }
}

console.log('Android setup complete!')
