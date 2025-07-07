
set -e

echo "=== JetShare VPS Setup Script ==="
echo "This script will set up your VPS for JetShare."

# Update and install necessary packages
echo "Updating package list and installing dependencies..."
sudo apt update

#install Java
echo "Installing Java..."
sudo apt install -y openjdk-17-jdk


#INstall Node
echo "Installing Node.js..."
curl -sL https://deb.nodesource.com/setup_18.x | sudo -E bash
sudo apt install -y nodejs

#install ngnix
echo "Installing Nginx..."
sudo apt install -y nginx

#install PM2
echo "Installing PM2..."
sudo npm install -g pm2

#install Maven
echo "Installing Maven..."
sudo apt install -y maven

#clone the repository
echo "Cloning JetShare repository..."
git clone hhtps://github.com/mehulljainn08/jetshare.git
cd jetshare

echo "Building JetShare..."
mvn clean package

#frontend-build
echo "Building frontend..."
cd ui
npm install
npm run build
cd..

echo "Setting up Nginx..."

if [ -f /etc/nginx/sites-enabled/default ]; then
    sudo rm /etc/nginx/sites-enabled/default
    echo "Default Nginx configuration removed."
fi

#create jetshare cnfig file with correct content
echo "Creating Nginx configuration for JetShare..."
cat <<EOF | sudo tee /etc/nginx/sites-available/jetshare
server {
    listen 80;
    server_name your_domain_or_ip;  # Replace with your domain or IP    

    #Backend API
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

    #Frontend
    location / {
        proxy_pass http://localhost:3000/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_cache_bypass \$http_upgrade;
    }

    # Additional security headers
    add_header X-Content-Type-Options nosniff;
    add_header X-Frame-Options DENY;
    add_header X-XSS-Protection "1; mode=block";
}
EOF

sudo ln -sf /etc/nginx/sites-available/jetshare /etc/nginx/sites-enabled/jetshare

sudo nginx -t
if [ $? -eq 0 ]; then
    sudo systemctl restart nginx
    echo "Nginx configuration is valid and has been restarted."
else
    echo "Nginx configuration is invalid. Please check the configuration file."
    exit 1
fi


echo "Starting JetShare backend with PM2..."
pm2 start --name jetshare-backend java -- -jar target/p2p-1.0-SNAPSHOT.jar

echo "Starting JetShare frontend with PM2..."
cd ui
pm2 start npm --name jetshare-frontend -- start
cd ..

pm2 save

echo "Setting up PM2 to start on boot..."
pm2 startup



echo "JetShare VPS setup completed successfully!"
echo "jetShare is now running on your VPS."
echo "You can access it at http://your_domain_or_ip "
echo "frontend: http://your_domain_or_ip"
echo "you can access your application using your lightsail public IP or domain name."

