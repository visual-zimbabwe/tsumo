package main

import (
	"bytes"
	"crypto/tls"
	"encoding/base64"
	"errors"
	"fmt"
	"net"
	"net/smtp"
	"os"
	"path/filepath"
	"strings"
)

type KindleConfig struct {
	To       string `json:"to"`
	From     string `json:"from"`
	SMTPHost string `json:"smtp_host"`
	SMTPPort int    `json:"smtp_port"`
}

func (c KindleConfig) Validate() error {
	if strings.TrimSpace(c.To) == "" || !strings.Contains(c.To, "@") {
		return errors.New("valid Send-to-Kindle email is required")
	}
	if strings.TrimSpace(c.From) == "" || !strings.Contains(c.From, "@") {
		return errors.New("valid sender email is required")
	}
	if strings.TrimSpace(c.SMTPHost) == "" {
		return errors.New("SMTP host is required")
	}
	if c.SMTPPort <= 0 || c.SMTPPort > 65535 {
		return errors.New("valid SMTP port is required")
	}
	return nil
}

func sendToKindle(filePath string, cfg KindleConfig, password string) error {
	if err := cfg.Validate(); err != nil {
		return err
	}
	if strings.TrimSpace(password) == "" {
		return errors.New("SMTP password is required")
	}
	if strings.TrimSpace(filePath) == "" {
		return errors.New("file path is required")
	}
	data, err := os.ReadFile(filePath)
	if err != nil {
		return fmt.Errorf("read book file: %w", err)
	}

	filename := filepath.Base(filePath)
	boundary := "TSUMO_KINDLE_MIME_BOUNDARY"

	var msg bytes.Buffer
	msg.WriteString(fmt.Sprintf("From: %s\r\n", cfg.From))
	msg.WriteString(fmt.Sprintf("To: %s\r\n", cfg.To))
	msg.WriteString(fmt.Sprintf("Subject: Send to Kindle: %s\r\n", filename))
	msg.WriteString("MIME-Version: 1.0\r\n")
	msg.WriteString(fmt.Sprintf("Content-Type: multipart/mixed; boundary=%q\r\n\r\n", boundary))

	msg.WriteString(fmt.Sprintf("--%s\r\n", boundary))
	msg.WriteString("Content-Type: text/plain; charset=utf-8\r\n\r\n")
	msg.WriteString("Delivered by Tsumo.\r\n\r\n")

	msg.WriteString(fmt.Sprintf("--%s\r\n", boundary))
	msg.WriteString("Content-Type: application/octet-stream\r\n")
	msg.WriteString("Content-Transfer-Encoding: base64\r\n")
	msg.WriteString(fmt.Sprintf("Content-Disposition: attachment; filename=%q\r\n\r\n", filename))

	encoded := base64.StdEncoding.EncodeToString(data)
	for i := 0; i < len(encoded); i += 76 {
		end := i + 76
		if end > len(encoded) {
			end = len(encoded)
		}
		msg.WriteString(encoded[i:end] + "\r\n")
	}
	msg.WriteString(fmt.Sprintf("--%s--\r\n", boundary))

	addr := fmt.Sprintf("%s:%d", cfg.SMTPHost, cfg.SMTPPort)
	auth := smtp.PlainAuth("", cfg.From, password, cfg.SMTPHost)

	if cfg.SMTPPort == 465 {
		conn, err := tls.Dial("tcp", addr, &tls.Config{ServerName: cfg.SMTPHost, MinVersion: tls.VersionTLS12})
		if err != nil {
			return err
		}
		defer conn.Close()
		c, err := smtp.NewClient(conn, cfg.SMTPHost)
		if err != nil {
			return err
		}
		defer c.Quit()
		if err := c.Auth(auth); err != nil {
			return err
		}
		if err := c.Mail(cfg.From); err != nil {
			return err
		}
		if err := c.Rcpt(cfg.To); err != nil {
			return err
		}
		w, err := c.Data()
		if err != nil {
			return err
		}
		if _, err := w.Write(msg.Bytes()); err != nil {
			return err
		}
		return w.Close()
	}

	host, _, _ := net.SplitHostPort(addr)
	if host == "" {
		host = cfg.SMTPHost
	}
	return smtp.SendMail(addr, auth, cfg.From, []string{cfg.To}, msg.Bytes())
}
