#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
从 schema.sql 生成 MyBatis-Plus 实体类与 Mapper 接口

为什么用生成器：
    18 张表 = 18 个实体 + 18 个 Mapper，共 36 个文件，全部是机械样板。
    手写既慢又容易漏字段/写错类型，而这部分没有任何设计决策，
    所以直接从建表语句派生，保证实体与表结构永远一致。

不生成的内容（需要人工设计，不走生成器）：
    Service / Controller / DTO —— 这些包含业务语义，必须手写。

用法: python gen_entities.py
"""

import os
import re

BASE = os.path.dirname(os.path.abspath(__file__))
SCHEMA = os.path.join(BASE, "..", "backend", "src", "main", "resources", "db", "schema.sql")
JAVA_ROOT = os.path.join(BASE, "..", "backend", "src", "main", "java", "com", "retail", "erp")
PKG = "com.retail.erp"

# 表名 -> 所属功能模块（决定 Java 包路径）
MODULE_OF = {
    "sys_user": "system",
    "refresh_token": "system",
    "category": "basic",
    "supplier": "basic",
    "product": "basic",
    "stock": "stock",
    "stock_batch": "stock",
    "stock_record": "stock",
    "stock_alert": "stock",
    "stock_check": "stock",
    "stock_check_item": "stock",
    "purchase_order": "purchase",
    "purchase_order_item": "purchase",
    "sale_order": "sale",
    "sale_order_item": "sale",
    "return_order": "returns",
    "return_order_item": "returns",
    "ai_log": "ai",
}

# SQL 类型 -> Java 类型
TYPE_MAP = {
    "BIGINT": "Long",
    "INT": "Integer",
    "TINYINT": "Integer",
    "SMALLINT": "Integer",
    "VARCHAR": "String",
    "CHAR": "String",
    "TEXT": "String",
    "LONGTEXT": "String",
    "DECIMAL": "BigDecimal",
    "NUMERIC": "BigDecimal",
    "DATE": "LocalDate",
    "DATETIME": "LocalDateTime",
    "TIMESTAMP": "LocalDateTime",
}

SKIP_PREFIX = ("PRIMARY KEY", "KEY ", "UNIQUE KEY", "INDEX ", "CONSTRAINT", ")")

COL_RE = re.compile(
    r"^\s*`?(?P<name>[a-z_][a-z0-9_]*)`?\s+"
    r"(?P<type>[A-Za-z]+)(?:\s*\((?P<args>[\d,\s]+)\))?"
    r"(?P<rest>.*?)(?:,)?$"
)
COMMENT_RE = re.compile(r"COMMENT\s+'((?:[^']|'')*)'", re.I)


def pascal(snake):
    return "".join(p.capitalize() for p in snake.split("_"))


def camel(snake):
    parts = snake.split("_")
    return parts[0] + "".join(p.capitalize() for p in parts[1:])


def parse_schema(path):
    with open(path, encoding="utf-8") as f:
        sql = f.read()

    # 去掉注释行，避免注释里的括号干扰
    lines = [ln for ln in sql.splitlines() if not ln.strip().startswith("--")]

    tables = {}
    cur = None
    for raw in lines:
        line = raw.rstrip()
        m = re.match(r"^\s*CREATE TABLE\s+`?(\w+)`?\s*\(", line, re.I)
        if m:
            cur = m.group(1)
            tables[cur] = []
            continue
        if cur is None:
            continue
        if line.strip().startswith(")") or line.strip().startswith("ENGINE"):
            cur = None
            continue
        if not line.strip() or line.strip().startswith(SKIP_PREFIX):
            continue

        cm = COL_RE.match(line)
        if not cm:
            continue
        name = cm.group("name")
        sql_type = cm.group("type").upper()
        if sql_type not in TYPE_MAP:
            continue
        comment = ""
        qm = COMMENT_RE.search(cm.group("rest") or "")
        if qm:
            comment = qm.group(1).replace("''", "'")
        tables[cur].append({
            "name": name,
            "sqlType": sql_type,
            "javaType": TYPE_MAP[sql_type],
            "comment": comment,
        })
    return tables


ENTITY_TPL = """package {pkg}.module.{module}.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.io.Serializable;
{imports}import lombok.Data;

/**
 * {comment}
 *
 * ⚠️ 本文件由 tools/gen_entities.py 依据 db/schema.sql 自动生成，请勿手工修改。
 *    表结构变更后请重新执行生成脚本。
 */
@Data
@TableName("{table}")
public class {clazz} implements Serializable {{

{fields}}}
"""

MAPPER_TPL = """package {pkg}.module.{module}.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import {pkg}.module.{module}.entity.{clazz};
import org.apache.ibatis.annotations.Mapper;

/**
 * {comment} Mapper
 *
 * ⚠️ 本文件由 tools/gen_entities.py 自动生成，请勿手工修改。
 *    复杂查询请写在 Service 里用 LambdaQueryWrapper 组装，或在本接口中新增 @Select 方法。
 */
@Mapper
public interface {clazz}Mapper extends BaseMapper<{clazz}> {{
}}
"""


def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)


def main():
    tables = parse_schema(SCHEMA)
    if not tables:
        raise SystemExit("未从 schema.sql 解析到任何表，请检查路径: " + SCHEMA)

    stats = []
    for table, cols in tables.items():
        if table not in MODULE_OF:
            print(f"  [跳过] {table} 未在 MODULE_OF 中登记模块")
            continue

        module = MODULE_OF[table]
        clazz = pascal(table)
        comment = f"{clazz} 实体"

        needs = set()
        field_lines = []
        for c in cols:
            jt = c["javaType"]
            if jt in ("BigDecimal", "LocalDate", "LocalDateTime"):
                needs.add(f"java.{'math.BigDecimal' if jt == 'BigDecimal' else 'time.' + jt}")

            annos = []
            if c["name"] == "id":
                annos.append("@TableId(type = IdType.AUTO)")
            if c["name"] == "version":
                annos.append("@Version")
            if c["name"] == "deleted":
                annos.append("@TableLogic(value = \"0\", delval = \"1\")")

            if c["name"] not in ("id",):
                # 字段名与列名不一致时必须显式声明（如 ai_log.log_type）
                if camel(c["name"]) != c["name"]:
                    annos.append(f'@TableField("{c["name"]}")')

            for a in annos:
                field_lines.append("    " + a)
            doc = f"    /** {c['comment']} */" if c["comment"] else ""
            if doc:
                field_lines.append(doc)
            field_lines.append(f"    private {jt} {camel(c['name'])};")
            field_lines.append("")

        imports = "".join(f"import {n};\n" for n in sorted(needs))

        entity = ENTITY_TPL.format(
            pkg=PKG, module=module, table=table, clazz=clazz,
            comment=comment, imports=imports,
            fields="\n".join(field_lines),
        )
        write(os.path.join(JAVA_ROOT, "module", module, "entity", clazz + ".java"), entity)

        mapper = MAPPER_TPL.format(pkg=PKG, module=module, clazz=clazz, comment=f"{clazz} 实体")
        write(os.path.join(JAVA_ROOT, "module", module, "mapper", clazz + "Mapper.java"), mapper)

        stats.append((table, clazz, module, len(cols)))

    print(f"共生成 {len(stats)} 张表的实体 + Mapper：\n")
    print(f"{'表名':<24}{'实体类':<22}{'模块':<12}字段数")
    print("-" * 66)
    for t, c, m, n in stats:
        print(f"{t:<24}{c:<22}{m:<12}{n}")
    print(f"\n输出目录: {JAVA_ROOT}")


if __name__ == "__main__":
    main()
