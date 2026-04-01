const nodemailer = require('nodemailer');

const {
  SMTP_HOST = 'smtp.zoho.com',
  SMTP_PORT = '465',
  SMTP_USER,
  SMTP_PASS,
  SMTP_FROM,
  APP_URL = 'http://localhost:5173'
} = process.env;

let transporter = null;

function getTransporter() {
  if (!transporter) {
    if (!SMTP_USER || !SMTP_PASS) {
      throw new Error('SMTP_USER and SMTP_PASS environment variables are required for email sending');
    }
    transporter = nodemailer.createTransport({
      host: SMTP_HOST,
      port: parseInt(SMTP_PORT, 10),
      secure: parseInt(SMTP_PORT, 10) === 465,
      auth: {
        user: SMTP_USER,
        pass: SMTP_PASS
      }
    });
  }
  return transporter;
}

async function sendPasswordResetEmail(toEmail, resetToken) {
  const resetUrl = `${APP_URL}?reset_token=${resetToken}`;
  const from = SMTP_FROM || SMTP_USER;

  await getTransporter().sendMail({
    from,
    to: toEmail,
    subject: 'Reset your Time Command password',
    text: `You requested a password reset.\n\nClick the link below to set a new password (expires in 1 hour):\n\n${resetUrl}\n\nIf you did not request this, you can safely ignore this email.`,
    html: `
      <p>You requested a password reset.</p>
      <p>Click the link below to set a new password (expires in 1 hour):</p>
      <p><a href="${resetUrl}">${resetUrl}</a></p>
      <p>If you did not request this, you can safely ignore this email.</p>
    `
  });
}

module.exports = { sendPasswordResetEmail };
