$ErrorActionPreference = "Stop"

Write-Host "1. Building and Testing Project..."
mvn clean package

Write-Host "2. Building Docker Image..."
docker build -t asia-south1-docker.pkg.dev/ecommerce-502707/ecommerce-repo/ecommerce-backend:latest .

Write-Host "3. Pushing Docker Image to Artifact Registry..."
docker push asia-south1-docker.pkg.dev/ecommerce-502707/ecommerce-repo/ecommerce-backend:latest

Write-Host "4. Deploying to Cloud Run..."
gcloud run deploy ecommerce-backend --image asia-south1-docker.pkg.dev/ecommerce-502707/ecommerce-repo/ecommerce-backend:latest --region asia-south1 --platform managed --allow-unauthenticated

Write-Host "Deployment Completed Successfully!"
