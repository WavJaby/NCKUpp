# ![Logo](https://wavjaby.github.io/NCKUpp/res/assets/icon/icon_64.svg) NCKU++

更優質的選課網站<br>
A new site for NCKU course enrollment

### [To NCKU++](https://wavjaby.github.io/NCKUpp/)

---

## API Documentation

API endpoint<br>
https://api.simon.chummydns.com/api

### Response Object

```
{
    "success": boolean
    "data": {}|null
    "msg": string
    "code": int
    "err": [string]
    "warn": [string]
}
```

<details>
<summary><code>GET</code> <code><b>/search</b></code> <code>Search course data</code></summary>

##### Parameters

> | name         | type     | data type | description                                               |
> |--------------|----------|-----------|-----------------------------------------------------------|
> | `courseName` | optional | string    | Course name                                               |
> | `instructor` | optional | string    | Instructor name                                           |
> | `dayOfWeek`  | optional | int[]     | Day of week [0~6]                                         |
> | `dept`       | optional | string    | Department ID                                             |
> | `grade`      | optional | string    | Course for grade                                          |
> | `section`    | optional | int[]     | Section of day [0~15]                                     |
> | `serial`     | optional | UrlEncode | Serial IDs {DEPT_ID}={SERIAL},{SERIAL}&{DEPT_ID}={SERIAL} |

##### Example

/search?dept=A9 <br>
/search?dayOfWeek=0,1&section=3,4 <br>

> [!WARNING]  
> Multiple dayOfWeek will make response time longer and likely to cause network error

</details>


<details>
<summary><code>GET</code> <code><b>/alldept</b></code> <code>Get all department ID</code></summary>
</details>


<details>
<summary><code>GET</code> <code><b>/nckuhub</b></code> <code>Get NCKU HUB data</code></summary>

##### Parameters

> | name | type     | data type | description      |
> |------|----------|-----------|------------------|
> | `id` | optional | string    | Course serial ID |

Give id and return NCKU HUB rating and comments
If no id provide, return available CourseSerialID <br>

</details>


<details>
<summary><code>GET</code> <code><b>/login</b></code> <code>Check login state</code></summary>

##### Parameters

> | name   | type     | data type | description                                                                                         |
> |--------|----------|-----------|-----------------------------------------------------------------------------------------------------|
> | `mode` | require  | string    | Login mode, legal value: course, stuId <br/> course: Course NCKU <br/> stuId: StudentIdentification |

</details>


<details>
<summary><code>POST</code> <code><b>/login</b></code> <code>Login</code></summary>

##### Parameters

> | name   | type     | data type | description                                                                                         |
> |--------|----------|-----------|-----------------------------------------------------------------------------------------------------|
> | `mode` | require  | string    | Login mode, legal value: course, stuId <br/> course: Course NCKU <br/> stuId: StudentIdentification |

##### Payload

Content-Type: application/x-www-form-urlencoded

```
username=[Account]&password=[Password]
```

</details>


<details>
<summary><code>GET</code> <code><b>/courseSchedule</b></code> <code>Get course schedule</code></summary>

##### Parameters

> | name  | type     | data type | description                             |
> |-------|----------|-----------|-----------------------------------------|
> | `pre` | optional | boolean   | If pre equals true, return pre-schedule |

</details>


<details>
<summary><code>GET</code> <code><b>/v0/socket</b></code> <code>Notify system api</code></summary>
</details>