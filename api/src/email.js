const nodemailer = require('nodemailer');

const SMTP_HOST = process.env.SMTP_HOST;
const SMTP_PORT = Number(process.env.SMTP_PORT || 465);
const SMTP_USER = process.env.SMTP_USER;
const SMTP_PASS = process.env.SMTP_PASS;
const SMTP_FROM = process.env.SMTP_FROM || SMTP_USER;
const APP_NAME = process.env.APP_NAME || 'YATT';
const PUBLIC_API_BASE_URL = process.env.PUBLIC_API_BASE_URL || 'http://localhost:3000';

function parseBoolean(value, defaultValue) {
  if (value === undefined || value === null || value === '') {
    return defaultValue;
  }
  const normalized = String(value).toLowerCase().trim();
  return normalized === 'true' || normalized === '1' || normalized === 'yes' || normalized === 'on';
}

const SMTP_SECURE = parseBoolean(process.env.SMTP_SECURE, SMTP_PORT === 465);

function isEmailConfigured() {
  return Boolean(SMTP_HOST && SMTP_USER && SMTP_PASS);
}

let transporter = null;

function getTransporter() {
  if (!transporter) {
    transporter = nodemailer.createTransport({
      host: SMTP_HOST,
      port: SMTP_PORT,
      secure: SMTP_SECURE,
      auth: {
        user: SMTP_USER,
        pass: SMTP_PASS
      }
    });
  }
  return transporter;
}

async function sendEmail({ to, subject, text, html }) {
  if (!isEmailConfigured()) {
    throw new Error('Email service is not configured');
  }
  return getTransporter().sendMail({
    from: SMTP_FROM,
    to,
    subject,
    text,
    html
  });
}

function buildPublicUrl(path) {
  const base = PUBLIC_API_BASE_URL.replace(/\/$/, '');
  const suffix = path.startsWith('/') ? path : `/${path}`;
  return `${base}${suffix}`;
}

async function sendConfirmationEmail({ to, token }) {
  const confirmUrl = buildPublicUrl(`/auth/confirm-email?token=${encodeURIComponent(token)}`);
  const subject = `${APP_NAME} - Confirm your email`;
  const text = [
    `Welcome to ${APP_NAME}!`,
    '',
    'Please confirm your email address by clicking the link below:',
    confirmUrl,
    '',
    'If you did not create this account, you can ignore this email.'
  ].join('\n');
  const html = [
    `<p>Welcome to ${APP_NAME}!</p>`,
    '<p>Please confirm your email address by clicking the link below:</p>',
    `<p><a href="${confirmUrl}">Confirm email</a></p>`,
    '<p>If you did not create this account, you can ignore this email.</p>'
  ].join('');
  return sendEmail({ to, subject, text, html });
}

async function sendPasswordResetEmail({ to, token }) {
  const resetUrl = buildPublicUrl(`/auth/reset-password?token=${encodeURIComponent(token)}`);
  const subject = `${APP_NAME} - Reset your password`;
  const text = [
    `We received a request to reset your ${APP_NAME} password.`,
    '',
    'Use the link below to choose a new password:',
    resetUrl,
    '',
    'If you did not request this, you can ignore this email.'
  ].join('\n');
  const html = [
    `<p>We received a request to reset your ${APP_NAME} password.</p>`,
    '<p>Use the link below to choose a new password:</p>',
    `<p><a href="${resetUrl}">Reset password</a></p>`,
    '<p>If you did not request this, you can ignore this email.</p>'
  ].join('');
  return sendEmail({ to, subject, text, html });
}

module.exports = {
  isEmailConfigured,
  sendConfirmationEmail,
  sendPasswordResetEmail
};
