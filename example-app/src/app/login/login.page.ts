import { Component, OnInit } from '@angular/core';
import { PushApp } from 'pushapp-ionic';
import { NavController } from '@ionic/angular';

@Component({
  selector: 'app-login',
  templateUrl: './login.page.html',
  standalone: false
})
export class LoginPage implements OnInit {

  username = '';
  password = ''; // optional for now

  constructor(private navCtrl: NavController) {}

  async ngOnInit() {
    // Check if user is already logged in
    const savedUsername = localStorage.getItem('username');
    if (savedUsername) {
      // User is already logged in, redirect to dashboard
      this.navCtrl.navigateRoot('/home');
      return;
    }

    console.log("Login page loaded → Initializing SDK...");

    try {
      const res = await PushApp.initialize({
        identifier: "demo$demo_1763369170735",
        sandbox: false
      });

      console.log("SDK Initialized:", res);
    } catch (err) {
      console.error("Initialize error:", err);
    }
  }

  async login() {
    if (!this.username) {
      alert("Username is required");
      return;
    }

    try {
      console.log("Calling plugin login...");

      const res = await PushApp.login({
        userId: this.username
      });

      console.log("Login response:", res);

      // Store username in localStorage for dashboard
      localStorage.setItem('username', this.username);

      // Navigate to home page (after login)
      this.navCtrl.navigateRoot('/home');

    } catch (err) {
      console.error("Login failed:", err);
      alert("Login failed");
    }
  }
}
