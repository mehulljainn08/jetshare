#!/bin/bash
set -e

echo "=== JetShare VPS Setup Script ==="
echo "This script will set up your VPS for JetShare."

# Update and install necessary packages
echo "Updating package list and installing dependencies..."
sudo apt update

# Install Java
echo "Installing Java..."
sudo apt install -y openjdk-17-jdk

# Install Node.js
echo "Installing Node.js..."
curl -sL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt install -y nodejs

# Install Nginx
echo "Installing Nginx..."
sudo apt install -y nginx

# Install PM2
echo "Installing PM2..."
sudo npm install -g pm2

# Install Maven
echo "Installing Maven..."
sudo apt install -y maven

# Clone the repository
if [ ! -d "jetshare/.git" ]; then
  git clone https://github.com/mehulljainn08/jetshare.git
fi
cd jetshare


# Build backend
# Build backend
echo "Building JetShare backend..."
cd p2p
mvn clean package
cd ..


# Build frontend
echo "Building frontend..."
cd ui
npm install
npm run build
cd ..

# Serve frontend using Nginx (static build)
echo "Configuring Nginx..."

# Remove default Nginx config if it exists
if [ -f /etc/nginx/sites-enabled/default ]; then
    sudo rm /etc/nginx/sites-enabled/default
    echo "Default Nginx configuration removed."
fi

# Replace this with your actual domain or IP
SERVER_NAME="13.235.40.111"

# Create Nginx config for JetShare
echo "Creating Nginx configuration for JetShare..."
cat <<EOF | sudo tee /etc/nginx/sites-available/jetshare
server {
    listen 80;
    server_name $SERVER_NAME;

    location /api/ {
        proxy_pass http://localhost:8080/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_cache_bypass \$http_upgrade;
    }

    location / {
        root /home/ubuntu/jetshare/ui/build;
        index index.html;
        try_files \$uri /index.html;
    }

    add_header X-Content-Type-Options nosniff;
    add_header X-Frame-Options DENY;
    add_header X-XSS-Protection "1; mode=block";
}
EOF

# Enable Nginx config
sudo ln -sf /etc/nginx/sites-available/jetshare /etc/nginx/sites-enabled/jetshare

# Test and reload Nginx
sudo nginx -t
if [ $? -eq 0 ]; then
    sudo systemctl restart nginx
    echo "Nginx restarted with new configuration."
else
    echo "Nginx configuration is invalid. Exiting..."
    exit 1
fi

# Start backend with PM2
echo "Starting JetShare backend with PM2..."
JAR_FILE=$(find target -name "*.jar" | head -n 1)
pm2 start --name jetshare-backend java -- -jar "$JAR_FILE"

# Save PM2 processes and setup startup
echo "Saving PM2 processes and enabling startup on boot..."
pm2 save
sudo su -c "env PATH=\$PATH:/usr/bin pm2 startup systemd -u ubuntu --hp /home/ubuntu"

echo ""
echo "✅ JetShare VPS setup completed successfully!"
echo "Backend running on http://$SERVER_NAME/api/"
echo "Frontend running on http://$SERVER_NAME/"


