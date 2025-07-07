#!/bin/bash

set -e

echo "📦 Starting JetShare VPS setup..."

# Clone repo if not present
if [ ! -d "jetshare/.git" ]; then
  git clone https://github.com/mehulljainn08/jetshare.git
fi

cd jetshare

# Build backend JAR
echo "🔨 Building backend..."
cd p2p
mvn clean package

cd ..

# Build frontend
echo "🌐 Building frontend..."
cd ui
npm install
npm run build
cd ..

# Setup Nginx
echo "⚙️ Configuring Nginx..."
NGINX_CONFIG="/etc/nginx/sites-available/jetshare"
cat <<EOF | sudo tee $NGINX_CONFIG
server {
    listen 80;
    server_name $HOSTNAME;

    location /upload {
        proxy_pass http://localhost:8080/upload;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    location /download {
        proxy_pass http://localhost:8080/download;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    location / {
        root /home/ubuntu/jetshare/ui/out;
        index index.html;
        try_files \$uri /index.html;
    }

    add_header X-Content-Type-Options nosniff;
    add_header X-Frame-Options DENY;
    add_header X-XSS-Protection "1; mode=block";
}
EOF

# Enable site
sudo ln -sf $NGINX_CONFIG /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx

# Start backend with PM2
echo "🚀 Starting backend..."
cd p2p
pm2 delete jetshare-backend || true
pm2 start java --name jetshare-backend -- -jar target/p2p-1.0-SNAPSHOT.jar

# Auto-start on boot
pm2 save
pm2 startup systemd -u ubuntu --hp /home/ubuntu

echo ""
echo "✅ JetShare VPS setup completed successfully!"
echo "🌐 Frontend: http://$(curl -s http://checkip.amazonaws.com)"
echo "📦 Backend:  http://<server-ip>/upload or /download/<port>"
echo "🛠️  Logs:    pm2 logs"
