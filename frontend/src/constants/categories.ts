export const categories = [
  {id: 'theater', label: '연극'},
  {id: 'dance', label: '무용'},
  {id: 'popular_dance', label: '대중무용'},
  {id: 'classical', label: '클래식'},
  {id: 'korean_traditional', label: '국악'},
  {id: 'concert', label: '콘서트'},
  {id: 'complex', label: '복합'},
  {id: 'circus_magic', label: '서커스/마술'},
  {id: 'musical', label: '뮤지컬'},
] as const

export type CategoryId = typeof categories[number]['id']

export const popularCategories = categories.filter(category =>
  ['theater', 'dance', 'classical', 'korean_traditional', 'circus_magic', 'musical'].includes(category.id),
)
