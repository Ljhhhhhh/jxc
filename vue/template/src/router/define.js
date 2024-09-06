import Layout from "@/layout/index"
import Redirect from "@/view/_app/redirect"
import Login from "@/view/_app/login/index"
import Page403 from "@/view/_app/403"
import Page404 from "@/view/_app/404"
import Page500 from "@/view/_app/500"
import routes from './module'
import {deepClone} from "@/util"

export function getDynamicRoutes() {
    return deepClone(routes)
}

//系统自有路由
export default [
    {
        path: '/redirect',
        component: Layout,
        children: [
            {
                path: '*',
                component: Redirect
            }
        ]
    },
    {
        path: '/login',
        alias: '/register',
        component: Login
    },
    {
        path: '/403',
        component: Page403
    },
    {
        path: '/404',
        component: Page404
    },
    {
        path: '/500',
        component: Page500
    }
]
