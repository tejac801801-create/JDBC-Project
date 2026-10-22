create database jdbc_projects;
use jdbc_projects;
create table books (
 book_id int primary key, title varchar(150) not null, author varchar(100) not null,
 category varchar(60), available_copies int not null default 1
);
create table members (
 member_id int primary key, member_name varchar(100) not null, email varchar(120) unique
);
create table book_issues (
 issue_id int primary key auto_increment, book_id int not null, member_id int not null,
 issue_date date not null, return_date date null, status varchar(20) not null default 'ISSUED',
 foreign key (book_id) references books(book_id),
 foreign key (member_id) references members(member_id)
);
create table products (
 product_id int primary key, product_name varchar(120) not null, category varchar(60),
 quantity int not null default 0, unit_price decimal(10,2) not null
);
create table sales (
 sale_id int primary key auto_increment, product_id int not null, quantity_sold int not null,
 sale_date timestamp default current_timestamp, total_amount decimal(10,2) not null,
 foreign key (product_id) references products(product_id)
);
