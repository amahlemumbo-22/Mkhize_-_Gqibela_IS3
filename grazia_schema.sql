-- GRAZIA ORDERS: brand-new schema. Your old restaurants_schema is NOT touched.
-- Run the whole script once in MySQL Workbench (File > Open SQL Script, then the lightning bolt).

CREATE DATABASE IF NOT EXISTS grazia_db CHARACTER SET utf8mb4;
USE grazia_db;

CREATE TABLE statuses (
  status_id INT AUTO_INCREMENT PRIMARY KEY,
  status_name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE persons (
  person_id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(50) NOT NULL,
  surname VARCHAR(50) NOT NULL,
  contact VARCHAR(20),
  email VARCHAR(100),
  password VARCHAR(255) NOT NULL
);

CREATE TABLE restaurants (
  restaurant_id INT AUTO_INCREMENT PRIMARY KEY,
  restaurant_name VARCHAR(100) NOT NULL,
  contact VARCHAR(20),
  email VARCHAR(100),
  address VARCHAR(200)
);

CREATE TABLE admins (
  admin_id INT AUTO_INCREMENT PRIMARY KEY,
  person_id INT NOT NULL UNIQUE,
  FOREIGN KEY (person_id) REFERENCES persons(person_id)
);

CREATE TABLE managers (            -- one manager <-> one restaurant
  manager_id INT AUTO_INCREMENT PRIMARY KEY,
  person_id INT NOT NULL UNIQUE,
  restaurant_id INT NOT NULL UNIQUE,
  FOREIGN KEY (person_id) REFERENCES persons(person_id),
  FOREIGN KEY (restaurant_id) REFERENCES restaurants(restaurant_id)
);

CREATE TABLE customers (
  customer_id INT AUTO_INCREMENT PRIMARY KEY,
  person_id INT NOT NULL UNIQUE,
  FOREIGN KEY (person_id) REFERENCES persons(person_id)
);

CREATE TABLE menus (
  menu_id INT AUTO_INCREMENT PRIMARY KEY,
  menu_name VARCHAR(100) NOT NULL,
  restaurant_id INT NOT NULL,
  menu_price DECIMAL(10,2) NOT NULL DEFAULT 0,
  FOREIGN KEY (restaurant_id) REFERENCES restaurants(restaurant_id)
);

CREATE TABLE menu_items (
  menu_item_id INT AUTO_INCREMENT PRIMARY KEY,
  menu_id INT NOT NULL,
  item_name VARCHAR(100) NOT NULL,
  description VARCHAR(255),
  item_price DECIMAL(10,2) NOT NULL,
  status_id INT NOT NULL,
  FOREIGN KEY (menu_id) REFERENCES menus(menu_id),
  FOREIGN KEY (status_id) REFERENCES statuses(status_id)
);

CREATE TABLE restaurant_tables (
  table_id INT AUTO_INCREMENT PRIMARY KEY,
  restaurant_id INT NOT NULL,
  capacity INT NOT NULL,
  location VARCHAR(100),
  status_id INT NOT NULL,
  FOREIGN KEY (restaurant_id) REFERENCES restaurants(restaurant_id),
  FOREIGN KEY (status_id) REFERENCES statuses(status_id)
);

CREATE TABLE orders (
  order_id INT AUTO_INCREMENT PRIMARY KEY,
  order_date DATE NOT NULL,
  order_time TIME NOT NULL,
  pickup_time TIME,
  total_amount DECIMAL(10,2) NOT NULL,
  customer_id INT NOT NULL,
  restaurant_id INT NOT NULL,
  status_id INT NOT NULL,
  FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
  FOREIGN KEY (restaurant_id) REFERENCES restaurants(restaurant_id),
  FOREIGN KEY (status_id) REFERENCES statuses(status_id)
);

CREATE TABLE order_items (         -- each line is an item OR a whole menu
  order_item_id INT AUTO_INCREMENT PRIMARY KEY,
  order_id INT NOT NULL,
  menu_item_id INT NULL,
  menu_id INT NULL,
  quantity INT NOT NULL,
  price_at_order DECIMAL(10,2) NOT NULL,
  FOREIGN KEY (order_id) REFERENCES orders(order_id),
  FOREIGN KEY (menu_item_id) REFERENCES menu_items(menu_item_id),
  FOREIGN KEY (menu_id) REFERENCES menus(menu_id)
);

CREATE TABLE payments (
  payment_id INT AUTO_INCREMENT PRIMARY KEY,
  amount DECIMAL(10,2) NOT NULL,
  payment_date DATE NOT NULL,
  payment_method VARCHAR(30),
  order_id INT NOT NULL,
  customer_id INT NOT NULL,
  restaurant_id INT NOT NULL,
  FOREIGN KEY (order_id) REFERENCES orders(order_id),
  FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
  FOREIGN KEY (restaurant_id) REFERENCES restaurants(restaurant_id)
);

CREATE TABLE reservations (
  reservation_id INT AUTO_INCREMENT PRIMARY KEY,
  table_id INT NOT NULL,
  customer_id INT NOT NULL,
  restaurant_id INT NOT NULL,
  reservation_date DATE NOT NULL,
  reservation_time TIME NOT NULL,
  number_of_guests INT NOT NULL,
  created_date DATE NOT NULL,
  status_id INT NOT NULL,
  FOREIGN KEY (table_id) REFERENCES restaurant_tables(table_id),
  FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
  FOREIGN KEY (restaurant_id) REFERENCES restaurants(restaurant_id),
  FOREIGN KEY (status_id) REFERENCES statuses(status_id)
);

-- ---------- starter data ----------
INSERT INTO statuses (status_name) VALUES
  ('AVAILABLE'),('NOT AVAILABLE'),('PENDING'),('CONFIRMED'),('PREPARING'),
  ('READY'),('COMPLETED'),('CANCELLED'),('APPROVED'),('DECLINED');

INSERT INTO restaurants (restaurant_name, contact, email, address) VALUES
  ('GRAZIA','0702335134','grazia@gmail.com','153 st Kugompo City'),
  ('ZAZA','0689382203','zaza@gmail.com','240 st Kugompo City');

-- Temporary passwords (already hashed): onela=Onela@Temp1  bonga=Bonga@Temp1  mbali=Mbali@Temp1  mumbo=Mumbo@Temp1
INSERT INTO persons (username, name, surname, contact, email, password) VALUES
  ('onela','Onele','Gqibela','0710000001','onelegqibela@admin.com','pbkdf2:p+BPsuXC+KAl5qJPZJTgnQ==:KAeocEJc26JjQqFavj8tnz4KlyfqHLXE9Vs6XojVVe0='),
  ('bonga','Bonga','Willie','0644382032','syabonga@gmail.com','pbkdf2:jq48wmSnHtnLI1VheXAU+w==:5WFT5Hk8MZDoI7aUbYE7XLWL2o95w+gGaPpPwILowiU='),
  ('mbali','Mbali','Zuma','0781237228','mbalizuma@gmail.com','pbkdf2:Q8ctK4YkuE+XVjILjYyLyw==:1GSIvjt9DhW7ewugFhyCMDiS4Xv7T72qR4ah3XsWbMk='),
  ('mumbo','Amahle','Mkhize','020343774','amahlemumbo@gmail.com','pbkdf2:aUOdu9tWzwWF8cJehAtriA==:y7U3U/I0vXucdkb5n73rOIzyWXnVDxR/fXhInitzvMk=');

INSERT INTO admins (person_id)    SELECT person_id FROM persons WHERE username='onela';
INSERT INTO managers (person_id, restaurant_id)
  SELECT person_id, 1 FROM persons WHERE username='bonga';
INSERT INTO managers (person_id, restaurant_id)
  SELECT person_id, 2 FROM persons WHERE username='mbali';
INSERT INTO customers (person_id) SELECT person_id FROM persons WHERE username='mumbo';

INSERT INTO menus (menu_name, restaurant_id, menu_price) VALUES ('BEEF STEW', 1, 100.00);
INSERT INTO menu_items (menu_id, item_name, description, item_price, status_id) VALUES (1, 'COKE', '500ml', 20.00, 1);
INSERT INTO restaurant_tables (restaurant_id, capacity, location, status_id) VALUES
  (1, 2, 'Window', 1), (1, 4, 'Main hall', 1), (2, 6, 'Terrace', 1);

-- ---------- a limited MySQL user for the app (so it does not need root) ----------
CREATE USER IF NOT EXISTS 'grazia_app'@'localhost' IDENTIFIED BY 'Grazia_App#2026x';
GRANT SELECT, INSERT, UPDATE, DELETE ON grazia_db.* TO 'grazia_app'@'localhost';
