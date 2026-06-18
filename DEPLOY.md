# Деплой на AWS EC2 с HTTPS (без домейн)

Използваме sslip.io — безплатна услуга, която дава DNS запис за всеки IP адрес.
Пример: `54.123.45.67.sslip.io` автоматично сочи към `54.123.45.67`.
Това ни позволява да вземем истински Let's Encrypt сертификат без собствен домейн.

---

## 1. Създай EC2 инстанция

1. Влез в AWS Console → EC2 → Launch Instance
2. Избери **Ubuntu 24.04 LTS**
3. Instance type: **t3.small** или по-голям (t3.micro може да не е достатъчен за Docker build)
4. Key pair: създай нов `.pem` файл и го запази — нужен е за SSH
5. Security Group — отвори следните портове:

| Port | Protocol | Source    | Описание         |
|------|----------|-----------|------------------|
| 22   | TCP      | My IP     | SSH достъп       |
| 80   | TCP      | 0.0.0.0/0 | HTTP (redirect)  |
| 443  | TCP      | 0.0.0.0/0 | HTTPS            |

6. Storage: минимум **20 GB** (Docker образите заемат място)
7. Launch Instance

---

## 2. Намери публичния IP на инстанцията

В AWS Console → EC2 → Instances → избери инстанцията → копирай **Public IPv4 address**.

Пример: `54.123.45.67`

Твоят sslip.io адрес ще е: `54.123.45.67.sslip.io`

> **Важно:** По подразбиране AWS дава динамичен IP, който се сменя при рестарт на инстанцията.
> Ако искаш постоянен IP — в AWS Console → Elastic IPs → Allocate → Associate с инстанцията (безплатно докато инстанцията върви).

---

## 3. Свържи се по SSH

```bash
chmod 400 /path/to/your-key.pem
ssh -i /path/to/your-key.pem ubuntu@54.123.45.67
```

---

## 4. Инсталирай Docker и Docker Compose

```bash
# Обнови пакетите
sudo apt update && sudo apt upgrade -y

# Инсталирай Docker
curl -fsSL https://get.docker.com | sudo sh

# Добави текущия потребител към docker групата (без sudo)
sudo usermod -aG docker $USER

# Излез и влез отново за да влязат в сила правата
exit
# свържи се отново по SSH
ssh -i /path/to/your-key.pem ubuntu@54.123.45.67

# Провери
docker --version
docker compose version
```

---

## 5. Инсталирай Certbot

```bash
sudo apt install -y certbot
```

---

## 6. Вземи SSL сертификат

Замени `54.123.45.67` с твоя реален IP:

```bash
sudo certbot certonly --standalone -d 54.123.45.67.sslip.io
```

Certbot ще поиска имейл адрес за известия при изтичане на сертификата.
Сертификатът е валиден **90 дни** и трябва да се поднови.

След успешно изпълнение сертификатите са в:
```
/etc/letsencrypt/live/54.123.45.67.sslip.io/fullchain.pem
/etc/letsencrypt/live/54.123.45.67.sslip.io/privkey.pem
```

---

## 7. Качи проекта на сървъра

**От твоята машина** (не от EC2):

```bash
# Архивирай проекта (без node_modules и target)
cd /path/to/AttendTrack
tar --exclude='./frontend/node_modules' \
    --exclude='./frontend/.next' \
    --exclude='./target' \
    --exclude='./.git' \
    -czf attendtrack.tar.gz .

# Качи на EC2
scp -i /path/to/your-key.pem attendtrack.tar.gz ubuntu@54.123.45.67:~
```

**На EC2:**

```bash
mkdir ~/attendtrack
tar -xzf attendtrack.tar.gz -C ~/attendtrack
cd ~/attendtrack
```

---

## 8. Създай .env.prod

```bash
cp .env.prod.example .env.prod
nano .env.prod
```

Попълни стойностите:

```env
DB_NAME=garant
DB_USERNAME=garant_user
DB_PASSWORD=някаква-силна-парола

# Генерирай с: openssl rand -base64 64
JWT_SECRET=

ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=силна-парола

# Замени с твоя IP
CORS_ALLOWED_ORIGINS=https://54.123.45.67.sslip.io
```

За `JWT_SECRET` изпълни:
```bash
openssl rand -base64 64
```

---

## 9. Копирай сертификатите

```bash
mkdir ~/attendtrack/certs
sudo cp /etc/letsencrypt/live/54.123.45.67.sslip.io/fullchain.pem ~/attendtrack/certs/
sudo cp /etc/letsencrypt/live/54.123.45.67.sslip.io/privkey.pem ~/attendtrack/certs/
sudo chown $USER:$USER ~/attendtrack/certs/*
```

---

## 10. Обнови nginx.conf

В `nginx.conf` смени `server_name _;` на твоя sslip.io адрес:

```bash
nano ~/attendtrack/nginx.conf
```

Намери реда:
```nginx
server_name _;
```

Смени на (в **двата** server блока):
```nginx
server_name 54.123.45.67.sslip.io;
```

---

## 11. Стартирай приложението

```bash
cd ~/attendtrack
docker compose -f docker-compose.prod.yml up --build -d
```

Първият build отнема няколко минути (сваля образи, компилира Java и Next.js).

Провери дали всичко върви:
```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f
```

Приложението е достъпно на: **https://54.123.45.67.sslip.io**

---

## 12. Поднови сертификата (на всеки 90 дни)

Let's Encrypt сертификатите изтичат след 90 дни. Автоматизирай подновяването:

```bash
# Тествай подновяването
sudo certbot renew --dry-run

# Добави cron задача за автоматично подновяване
sudo crontab -e
```

Добави реда:
```
0 3 * * * certbot renew --quiet && cp /etc/letsencrypt/live/54.123.45.67.sslip.io/fullchain.pem /home/ubuntu/attendtrack/certs/ && cp /etc/letsencrypt/live/54.123.45.67.sslip.io/privkey.pem /home/ubuntu/attendtrack/certs/ && docker compose -f /home/ubuntu/attendtrack/docker-compose.prod.yml restart nginx
```

Това проверява всяка нощ в 3:00 и подновява ако е нужно.

---

## Полезни команди

```bash
# Спри приложението
docker compose -f docker-compose.prod.yml down

# Рестартирай без да трие базата
docker compose -f docker-compose.prod.yml restart

# Виж логове на конкретна услуга
docker compose -f docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.prod.yml logs -f frontend
docker compose -f docker-compose.prod.yml logs -f nginx

# Rebuild само на frontend след промяна
docker compose -f docker-compose.prod.yml up --build -d frontend

# Влез в базата данни
docker compose -f docker-compose.prod.yml exec postgres psql -U garant_user -d garant
```
