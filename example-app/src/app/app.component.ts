import { Component, OnInit } from '@angular/core';
import { NavController } from '@ionic/angular';
import { slackWebhookInitOption } from '../environments/slack-webhook';
import { initializeAndRegisterPushApp } from './pushapp-setup';

@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  styleUrls: ['app.component.scss'],
  standalone: false,
})
export class AppComponent implements OnInit {
  constructor(
    private navCtrl: NavController
  ) {}

  async ngOnInit() {
    // Check if user is already logged in
    const savedUsername = localStorage.getItem('username');
    
    if (savedUsername) {
      console.log('User already logged in:', savedUsername);
      
      // Initialize SDK
      try {
        await initializeAndRegisterPushApp({
          appId: "demo_1763369170735",
          sandbox: false,
          ...slackWebhookInitOption(),
        });
        
        // // Re-login with saved username
        // await PushApp.login({
        //   userId: savedUsername
        // });
        
        // Navigate to dashboard
        this.navCtrl.navigateRoot('/home');
      } catch (err) {
        console.error('Auto-login failed:', err);
        // If auto-login fails, clear saved username and go to login
        localStorage.removeItem('username');
        this.navCtrl.navigateRoot('/login');
      }
    } else {
      // No saved user, go to login page
      this.navCtrl.navigateRoot('/login');
    }
  }
}
