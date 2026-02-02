import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'org.yatt.app',
  appName: 'YATT',
  webDir: 'dist',
  server: {
    // For development, point to your API server
    // Remove or change this for production
    cleartext: true,
    androidScheme: 'http'
  },
  plugins: {
    LocalNotifications: {
      smallIcon: "ic_stat_timer",
      iconColor: "#4a9eff"
    }
  }
};

export default config;
