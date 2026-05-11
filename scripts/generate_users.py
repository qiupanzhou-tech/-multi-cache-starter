"""生成10万条用户数据并写入MySQL"""
import pymysql
import random
import time

DB_CONFIG = {
    "host": "localhost", "user": "root", "password": "110110",
    "database": "multi_cache", "charset": "utf8mb4"
}

# 常见姓氏和名字用字
SURNAMES = ["王","李","张","刘","陈","杨","赵","黄","周","吴","徐","孙","胡","朱","高","林","何","郭","马","罗",
            "梁","宋","郑","谢","韩","唐","冯","于","董","萧","程","曹","袁","邓","许","傅","沈","曾","彭","吕"]
GIVEN_CHARS = ["伟","芳","娜","秀英","敏","静","丽","强","磊","军","洋","勇","艳","杰","娟","涛","明","超","秀兰",
               "霞","平","刚","桂英","文","建华","玉兰","飞","兰花","斌","鑫","志强","宇","鹏","泽宇","梓涵","一诺",
               "浩然","奕辰","子涵","雨桐","欣怡","可欣","晨曦","紫涵","诗涵","艺涵"]

PHONE_PREFIXES = ["130","131","132","133","135","136","137","138","139","150","151","152","155","156",
                  "157","158","159","166","176","177","178","180","181","182","183","185","186","187",
                  "188","189","191","193","195","198","199"]

EMAIL_DOMAINS = ["qq.com","163.com","126.com","gmail.com","sina.com","sohu.com","aliyun.com","foxmail.com"]

def gen_username(i):
    surname = random.choice(SURNAMES)
    length = random.choice([1, 2])
    name = surname + "".join(random.choices(GIVEN_CHARS, k=length))
    # 加序号保证唯一
    return f"{name}_{i}"

def gen_phone():
    return random.choice(PHONE_PREFIXES) + "".join(str(random.randint(0,9)) for _ in range(8))

def gen_email(username):
    name_part = username.split("_")[0]
    return f"{name_part}{random.randint(0,9999)}@{random.choice(EMAIL_DOMAINS)}"

BATCH_SIZE = 5000
TOTAL = 100000

def main():
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    # 清空旧数据
    cursor.execute("TRUNCATE TABLE user")
    print(f"开始生成 {TOTAL} 条用户数据...")
    start = time.time()

    sql = "INSERT INTO user(username, phone, email, create_time) VALUES "
    rows = []
    for i in range(1, TOTAL + 1):
        uname = gen_username(i)
        phone = gen_phone()
        email = gen_email(uname)
        t = f"2024-{random.randint(1,12):02d}-{random.randint(1,28):02d} {random.randint(0,23):02d}:{random.randint(0,59):02d}:{random.randint(0,59):02d}"
        rows.append(f"('{uname}','{phone}','{email}','{t}')")

        if len(rows) >= BATCH_SIZE or i == TOTAL:
            cursor.execute(sql + ",".join(rows))
            conn.commit()
            elapsed = time.time() - start
            speed = i / elapsed
            print(f"\r已写入 {i}/{TOTAL} ({100*i//TOTAL}%) — {speed:.0f} 条/秒", end="", flush=True)
            rows = []

    # 验证
    cursor.execute("SELECT COUNT(*) FROM user")
    count = cursor.fetchone()[0]
    elapsed = time.time() - start
    print(f"\n\n完成！共写入 {count} 条，耗时 {elapsed:.1f} 秒")

    # 查看样数据
    cursor.execute("SELECT * FROM user LIMIT 3")
    for row in cursor.fetchall():
        print(row)

    conn.close()

if __name__ == "__main__":
    main()
