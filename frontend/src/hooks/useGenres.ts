
import {useEffect, useState} from 'react'
import {getGenres, type Genre} from '../api/genreApi.ts'
import {toMessage} from '../api/apiError.ts'

export function useGenres() {
    const [genres, setGenres] = useState<Genre[]>([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')

    useEffect(() => {
        let active = true

        getGenres()
            .then((items) => {
                if (active) setGenres(items)
            })
            .catch((err: unknown) => {
                if (active) {
                    setError(toMessage(err, '장르 목록을 불러오지 못했습니다'))
                }
            })
            .finally(() => {
                if (active) setLoading(false)
            })

        return () => {
            active = false
        }
    }, [])

    return {genres, loading, error}
}
